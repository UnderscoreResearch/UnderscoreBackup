package com.underscoreresearch.backup.utils.state;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.service.api.model.ReleaseFileItem;

@Slf4j
public class OsxState extends MachineState {
    public OsxState(boolean pauseOnBattery) {
        super(pauseOnBattery);
    }

    @Override
    protected double getMaxCpuUsage() {
        return 1.5;
    }

    @Override
    public boolean getOnBattery() {
        try {
            Process proc = Runtime.getRuntime().exec("pmset -g ac");

            try (BufferedReader stdInput = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {

                String s;
                while ((s = stdInput.readLine()) != null) {
                    if (s.contains("No adapter")) {
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
        Optional<ReleaseFileItem> ret;
        if ("aarch64".equals(System.getProperty("os.arch"))) {
            ret = files.stream().filter(file -> file.getName().endsWith(".arm64.pkg")).findAny();
        } else {
            ret = files.stream().filter(file -> file.getName().endsWith(".x86_64.pkg")).findAny();
        }
        return ret.orElse(null);
    }

    @Override
    public void lowPriority() {
        try {
            Process process = Runtime.getRuntime()
                    .exec(String.format("renice +10 -p %s", ProcessHandle.current().pid()));
            if (process.waitFor() != 0) {
                throw new IOException();
            }
        } catch (IOException | InterruptedException e) {
            log.warn("Can't change process to low priority", e);
        }
    }
}
