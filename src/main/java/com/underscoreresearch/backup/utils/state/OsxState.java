package com.underscoreresearch.backup.utils.state;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class OsxState extends MachineState {
    public OsxState(boolean pauseOnBattery) {
        super(pauseOnBattery);
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
        } catch (IOException ex) {
        }
        return false;
    }
}
