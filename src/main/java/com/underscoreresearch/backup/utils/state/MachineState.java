package com.underscoreresearch.backup.utils.state;

import java.time.Duration;
import java.time.Instant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.configuration.InstanceFactory;

@Slf4j
@RequiredArgsConstructor
public class MachineState {
    private static final Duration MINIMUM_WAIT = Duration.ofSeconds(30);
    private final boolean pauseOnBattery;

    private boolean loggedOnBattery;
    private Instant nextCheck = Instant.MIN;
    private boolean lastValue;

    public boolean getOnBattery() {
        return false;
    }

    public void waitForPower() {
        if (pauseOnBattery) {
            if (occassionallyGetOnBattery()) {
                synchronized (this) {
                    if (!loggedOnBattery) {
                        loggedOnBattery = true;
                        log.info("Pausing until power is restored");
                    }
                }
                do {
                    try {
                        Thread.sleep(MINIMUM_WAIT.toMillis());
                    } catch (InterruptedException e) {
                        log.warn("Failed to wait", e);
                    }
                    if (InstanceFactory.isShutdown()) {
                        return;
                    }
                } while (occassionallyGetOnBattery());
                synchronized (this) {
                    if (loggedOnBattery) {
                        loggedOnBattery = false;
                        log.info("Continuing after restored power");
                    }
                }
            }
        }
    }

    private synchronized boolean occassionallyGetOnBattery() {
        if (nextCheck.isBefore(Instant.now())) {
            lastValue = getOnBattery();
            nextCheck = Instant.now().plus(MINIMUM_WAIT);
        }

        return lastValue;
    }
}
