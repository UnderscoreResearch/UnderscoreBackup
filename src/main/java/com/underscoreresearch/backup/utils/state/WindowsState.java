package com.underscoreresearch.backup.utils.state;

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

@Slf4j
public class WindowsState extends MachineState {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    public WindowsState(boolean pauseOnBattery) {
        super(pauseOnBattery);
    }

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

    @Override
    public ReleaseFileItem getDistribution(List<ReleaseFileItem> files) {
        Optional<ReleaseFileItem> ret = files.stream().filter(file -> file.getName().endsWith(".exe")).findAny();
        return ret.orElse(null);
    }

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

    @Override
    public boolean supportsAutomaticUpgrade() {
        return InstanceFactory.getInstance(SERVICE_MODE, Boolean.class);
    }

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

    @Override
    public FileChangePoller createPoller() throws IOException {
        return new WindowsFileChangePoller();
    }

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