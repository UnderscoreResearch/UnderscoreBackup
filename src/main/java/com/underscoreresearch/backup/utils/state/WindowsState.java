package com.underscoreresearch.backup.utils.state;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WindowsState extends MachineState {
    public WindowsState(boolean pauseOnBattery) {
        super(pauseOnBattery);
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
        } catch (IOException ex) {
        }
        return false;
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
}
