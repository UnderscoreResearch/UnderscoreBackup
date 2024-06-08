package com.underscoreresearch.backup.utils.state;

import static com.underscoreresearch.backup.configuration.CommandLineModule.SERVICE_MODE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl;
import com.underscoreresearch.backup.service.api.model.ReleaseFileItem;
import com.underscoreresearch.backup.service.api.model.ReleaseResponse;

@Slf4j
public class LinuxState extends MachineState {
    public LinuxState(boolean pauseOnBattery) {
        super(pauseOnBattery);
    }

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

    @Override
    public ReleaseFileItem getDistribution(List<ReleaseFileItem> files) {
        Optional<ReleaseFileItem> ret;
        if (new File("/usr/bin/dpkg").exists())
            ret = files.stream().filter(file -> file.getName().endsWith(".deb")).findAny();
        else
            ret = files.stream().filter(file -> file.getName().endsWith(".rpm")).findAny();
        return ret.orElse(null);
    }

    @Override
    public boolean supportsAutomaticUpgrade() {
        return InstanceFactory.getInstance(SERVICE_MODE, Boolean.class);
    }

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
