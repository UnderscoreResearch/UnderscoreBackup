package com.underscoreresearch.backup.machinestate;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl;
import com.underscoreresearch.backup.service.api.model.ReleaseFileItem;
import com.underscoreresearch.backup.service.api.model.ReleaseResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static com.underscoreresearch.backup.configuration.CommandLineModule.SERVICE_MODE;

/**
 * Linux-specific implementation of MachineState.
 * Provides Linux-specific functionality for battery status detection,
 * package management, and process priority control.
 */
@Slf4j
public class LinuxState extends MachineState {
    /**
     * Creates a new LinuxState with the specified battery pause setting.
     *
     * @param pauseOnBattery Whether to pause operations when on battery power
     */
    public LinuxState(boolean pauseOnBattery) {
        super(pauseOnBattery);
    }

    /**
     * Checks if the system is running on battery power by reading from the Linux power supply subsystem.
     *
     * @return True if the system is running on battery, false otherwise
     */
    @Override
    public boolean getOnBattery() {
        try {
            try (BufferedReader reader = new BufferedReader(new FileReader(new File("/sys/class/power_supply/AC/online")))) {
                String line = reader.readLine();
                return line != null && line.startsWith("0");
            }
        } catch (IOException exc) {
            return false;
        }
    }

    /**
     * Selects the appropriate Linux distribution package (DEB or RPM) based on the system architecture
     * and installed package manager.
     *
     * @param files List of available release files
     * @return The selected distribution file, or null if none is suitable
     */
    @Override
    public ReleaseFileItem getDistribution(List<ReleaseFileItem> files) {
        Optional<ReleaseFileItem> ret;

        String arch = System.getProperty("os.arch");
        String rpmSearch;
        String debSearch;
        if ("aarch64".equals(arch)) {
            rpmSearch = ".aarch64.rpm";
            debSearch = "_arm64.deb";
        } else {
            rpmSearch = ".x86_64.rpm";
            debSearch = "_amd64.deb";
        }

        if (new File("/usr/bin/dpkg").exists())
            ret = files.stream().filter(file -> file.getName().endsWith(debSearch)).findAny();
        else
            ret = files.stream().filter(file -> file.getName().endsWith(rpmSearch)).findAny();
        return ret.orElse(null);
    }

    /**
     * Checks if automatic upgrades are supported on this Linux system.
     * Upgrades are supported when running in service mode.
     *
     * @return True if automatic upgrades are supported, false otherwise
     */
    @Override
    public boolean supportsAutomaticUpgrade() {
        return InstanceFactory.getInstance(SERVICE_MODE, Boolean.class);
    }

    /**
     * Performs an automatic upgrade by downloading and staging the appropriate package.
     * The actual upgrade is performed by a cron job.
     *
     * @param response The release response containing upgrade information
     * @throws IOException If an error occurs during the upgrade
     */
    @Override
    public void upgrade(ReleaseResponse response) throws IOException {
        ReleaseFileItem download = getDistribution(response.getFiles());

        if (download != null) {

            File tempFile;
            if (download.getName().endsWith(".deb")) {
                tempFile = File.createTempFile("underscorebackup", ".deb");

                ServiceManagerImpl.downloadRelease(response, download, tempFile);

                executeUpdateProcess(new String[]{"mv", tempFile.toString(), "/var/cache/underscorebackup/upgradedversion.deb"});
            } else {
                tempFile = File.createTempFile("underscorebackup", ".rpm");

                ServiceManagerImpl.downloadRelease(response, download, tempFile);

                executeUpdateProcess(new String[]{"mv", tempFile.toString(), "/var/cache/underscorebackup/upgradedversion.rpm"});
            }

            log.info("Upgrade staged from cron job update");
        }
    }

    /**
     * Sets the process to run at a lower priority using the renice command.
     */
    @Override
    public void lowPriority() {
        try {
            Process process = Runtime.getRuntime()
                    .exec(new String[]{
                            "renice", "+10", "-p", Long.toString(ProcessHandle.current().pid())
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
}
