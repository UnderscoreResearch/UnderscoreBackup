package com.underscoreresearch.backup.utils.state;

import java.io.Closeable;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.changepoller.FileChangePoller;
import com.underscoreresearch.backup.file.changepoller.FsChangePoller;
import com.underscoreresearch.backup.service.api.model.ReleaseFileItem;
import com.underscoreresearch.backup.utils.PausedStatusLogger;

@Slf4j
@RequiredArgsConstructor
public class MachineState {
    private static final Duration MINIMUM_WAIT = Duration.ofSeconds(2);
    private final boolean pauseOnBattery;

    private boolean loggedOnBattery;
    private Instant nextCheck = Instant.MIN;
    private Instant nextCpuCheck = Instant.MIN;
    private boolean lastValue;
    private double lastCpuUsage;

    protected double getMaxCpuUsage() {
        return 0.2;
    }

    public boolean getOnBattery() {
        return false;
    }

    public double getCpuUsage() {
        try {
            com.sun.management.OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(
                    com.sun.management.OperatingSystemMXBean.class);
            double cpuLoad = osBean.getCpuLoad();
            double processLoad = osBean.getProcessCpuLoad();

            return Math.max(cpuLoad - processLoad, 0);
        } catch (IllegalArgumentException ignored) {
        }
        return Double.NaN;
    }

    public void waitForRunCheck() {
        if (pauseOnBattery) {
            if (occasionallyGetOnBattery() || occasionallyGetCpuUsage() > getMaxCpuUsage()) {
                String reason;
                synchronized (this) {
                    if (occasionallyGetOnBattery())
                        reason = "Pausing until power is restored";
                    else
                        reason = "Pausing until CPU usage goes down";

                    if (!loggedOnBattery) {
                        loggedOnBattery = true;
                        log.info(reason);
                    }
                }
                try (Closeable ignored = PausedStatusLogger.startPause(reason)) {
                    do {
                        try {
                            Thread.sleep(MINIMUM_WAIT.toMillis());
                        } catch (InterruptedException e) {
                            log.warn("Failed to wait", e);
                        }
                        if (InstanceFactory.isShutdown()) {
                            return;
                        }
                    } while (occasionallyGetOnBattery() || occasionallyGetCpuUsage() > getMaxCpuUsage());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                synchronized (this) {
                    if (loggedOnBattery) {
                        loggedOnBattery = false;
                        log.info("Continuing after pausing for power or CPU usage");
                    }
                }
            }
        }
    }

    public ReleaseFileItem getDistribution(List<ReleaseFileItem> files) {
        Optional<ReleaseFileItem> ret = files.stream().filter(file -> file.getName().endsWith(".tar")).findAny();
        if (ret.isPresent())
            return ret.get();
        ret = files.stream().filter(file -> file.getName().endsWith(".zip")).findAny();
        return ret.orElse(null);
    }

    public void lowPriority() {
    }

    private synchronized double occasionallyGetCpuUsage() {
        if (nextCpuCheck.isBefore(Instant.now())) {
            lastCpuUsage = getCpuUsage();
            nextCpuCheck = Instant.now().plus(MINIMUM_WAIT);
        }

        return lastCpuUsage;
    }

    private synchronized boolean occasionallyGetOnBattery() {
        if (nextCheck.isBefore(Instant.now())) {
            lastValue = getOnBattery();
            nextCheck = Instant.now().plus(MINIMUM_WAIT);
        }

        return lastValue;
    }

    public FileChangePoller createPoller() throws IOException {
        if (FsChangePoller.isSupported())
            return new FsChangePoller();
        throw new UnsupportedOperationException("Not supported on this platform");
    }
}
