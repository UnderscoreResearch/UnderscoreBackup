package com.underscoreresearch.backup.utils.state;

import static com.underscoreresearch.backup.configuration.CommandLineModule.SERVICE_MODE;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.changepoller.FileChangePoller;
import com.underscoreresearch.backup.file.changepoller.WindowsFileChangePoller;
import com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl;
import com.underscoreresearch.backup.service.api.model.ReleaseFileItem;
import com.underscoreresearch.backup.service.api.model.ReleaseResponse;

@Slf4j
public class WindowsState extends MachineState {
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
            if (process.waitFor() != 0) {
                throw new IOException();
            }
        } catch (IOException | InterruptedException e) {
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

            executeUpdateProcess(new String[]{"cmd", "/c", "start", "\"Underscore Backup Installer\"", "/b", tempFile.toString(),
                    "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/SP-", "/LOG=\"" + tempFile + ".log\""});
        }
    }

    @Override
    public FileChangePoller createPoller() throws IOException {
        return new WindowsFileChangePoller();
    }
}
