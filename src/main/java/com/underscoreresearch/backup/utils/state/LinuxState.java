package com.underscoreresearch.backup.utils.state;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class LinuxState extends MachineState {
    public LinuxState(boolean pauseOnBattery) {
        super(pauseOnBattery);
    }

    @Override
    public boolean getOnBattery() {
        try {
            try (BufferedReader reader = new BufferedReader(new FileReader(new File("/sys/class/power_supply/AC/online")))) {
                String line = reader.readLine();
                if (line != null && line.startsWith("0")) {
                    return true;
                }
                return false;
            }
        } catch (IOException exc) {
            return false;
        }
    }
}
