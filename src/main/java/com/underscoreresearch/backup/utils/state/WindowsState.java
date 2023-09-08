package com.underscoreresearch.backup.utils.state;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.file.changepoller.FileChangePoller;
import com.underscoreresearch.backup.file.changepoller.WindowsFileChangePoller;
import com.underscoreresearch.backup.service.api.model.ReleaseFileItem;

@Slf4j
public class WindowsState extends MachineState {
    public WindowsState(boolean pauseOnBattery) {
        super(pauseOnBattery);
    }

    @Override
    protected double getMaxCpuUsage() {
        return 0.75;
    }

    @Override
    public boolean getOnBattery() {
        try {
            Process proc = Runtime.getRuntime().exec(
                    "WMIC /NameSpace:\"\\\\root\\WMI\" Path BatteryStatus Get PowerOnline");

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
                    .exec(String.format("wmic process where processid=%d CALL setpriority \"idle\"", ProcessHandle.current().pid()));
            if (process.waitFor() != 0) {
                throw new IOException();
            }
        } catch (IOException | InterruptedException e) {
            log.warn("Can't change process to low priority", e);
        }
    }

    @Override
    public FileChangePoller createPoller() throws IOException {
        return new WindowsFileChangePoller();
    }
}
