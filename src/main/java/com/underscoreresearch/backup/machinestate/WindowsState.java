package com.underscoreresearch.backup.machinestate;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.changepoller.FileChangePoller;
import com.underscoreresearch.backup.file.changepoller.WindowsFileChangePoller;
import com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl;
import com.underscoreresearch.backup.service.api.model.ReleaseFileItem;
import com.underscoreresearch.backup.service.api.model.ReleaseResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static com.underscoreresearch.backup.configuration.CommandLineModule.SERVICE_MODE;
import static com.underscoreresearch.backup.io.IOUtils.executeQuietProcess;

/**
 * Windows-specific implementation of MachineState.
 * Provides Windows-specific functionality for battery status detection,
 * process priority control, file permissions, and automatic upgrades.
 */
@Slf4j
public class WindowsState extends MachineState {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    /**
     * Creates a new WindowsState with the specified battery pause setting.
     *
     * @param pauseOnBattery Whether to pause operations when on battery power
     */
    public WindowsState(boolean pauseOnBattery) {
        super(pauseOnBattery);
    }

    /**
     * Checks if the system is running on battery power by querying the Windows Management
     * Instrumentation (WMI) for battery status.
     *
     * @return True if the system is running on battery, false otherwise
     */
    @Override
    public boolean getOnBattery() {
        try {
            Process proc = Runtime.getRuntime().exec(
                    new String[]{
                            "wmic",
                            "/NameSpace:\"\\\\root\\WMI\"",
                            "Path",
                            "BatteryStatus",
                            "Get",
                            "PowerOnline"
                    });

            try (BufferedReader stdInput = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {

                String s;
                while ((s = stdInput.readLine()) != null) {
                    if (s.contains("FALSE")) {
                        return true;
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    /**
     * Selects the appropriate Windows distribution package (EXE installer).
     *
     * @param files List of available release files
     * @return The selected distribution file, or null if none is suitable
     */
    @Override
    public ReleaseFileItem getDistribution(List<ReleaseFileItem> files) {
        Optional<ReleaseFileItem> ret = files.stream().filter(file -> file.getName().endsWith(".exe")).findAny();
        return ret.orElse(null);
    }

    /**
     * Sets the process to run at a lower priority using Windows Management
     * Instrumentation (WMI).
     */
    @Override
    public void lowPriority() {
        try {
            Process process = Runtime.getRuntime()
                    .exec(new String[]{
                            "wmic", "process", "where", String.format("processid=%d", ProcessHandle.current().pid()), "CALL", "setpriority", "idle"
                    });
            try {
                if (process.waitFor() != 0) {
                    throw new IOException();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted changing process to low priority", e);
            }
        } catch (IOException e) {
            log.warn("Can't change process to low priority", e);
        }
    }

    /**
     * Checks if automatic upgrades are supported on this Windows system.
     * Upgrades are supported when running in service mode.
     *
     * @return True if automatic upgrades are supported, false otherwise
     */
    @Override
    public boolean supportsAutomaticUpgrade() {
        return InstanceFactory.getInstance(SERVICE_MODE, Boolean.class);
    }

    /**
     * Performs an automatic upgrade by downloading the installer and scheduling
     * it to run with elevated privileges using the Windows Task Scheduler.
     *
     * @param response The release response containing upgrade information
     * @throws IOException If an error occurs during the upgrade
     */
    @Override
    public void upgrade(ReleaseResponse response) throws IOException {
        ReleaseFileItem download = getDistribution(response.getFiles());
        if (download != null) {
            File tempFile = File.createTempFile("underscorebackup", ".exe");

            ServiceManagerImpl.downloadRelease(response, download, tempFile);

            String command = String.format("'%s' /verysilent /suppressmsgboxes /norestart /sp- /log='%s.log'",
                    tempFile, tempFile);

            // Schedule the upgrade to run in two minutes using the Windows scheduler and with
            // admin privileges.
            String twoMinutes = TIME_FORMATTER.format(Instant.now().plus(Duration.ofMinutes(2)));
            executeUpdateProcess(new String[]{"schtasks",
                    "/create", "/sc", "once", "/f",
                    "/tr", command,
                    "/tn", "Underscore Backup Upgrade",
                    "/st", twoMinutes,
                    "/rl", "HIGHEST",
                    "/ru", "SYSTEM"});
        }
    }

    /**
     * Creates a Windows-specific file change poller.
     *
     * @return A WindowsFileChangePoller instance
     * @throws IOException If an error occurs creating the poller
     */
    @Override
    public FileChangePoller createPoller() throws IOException {
        return new WindowsFileChangePoller();
    }

    /**
     * Sets file permissions to be accessible only by the owner using Windows ACLs.
     *
     * @param file The file to modify permissions for
     * @throws IOException If an error occurs setting the permissions
     */
    @Override
    public void setOwnerOnlyPermissions(File file) throws IOException {
        try {
            String username = System.getProperty("user.name");
            if (!username.endsWith("$")) {
                String permission = file.isDirectory() ? "(OI)(CI)F" : "F";
                executeQuietProcess("Owner only permissions", new String[]{
                        "icacls",
                        file.getCanonicalPath(),
                        "/inheritance:d",
                        "/grant:r",
                        username + ":" + permission
                });
            }
            executeQuietProcess("Owner only permissions", new String[]{
                    "icacls",
                    file.getCanonicalPath(),
                    "/Remove",
                    "Authenticated Users",
                    "/Remove",
                    "Users",
            });
        } catch (IOException e) {
            throw new IOException(String.format("Failed to set owner only permissions for \"%s\"", file.getCanonicalPath()), e);
        }
    }
}
