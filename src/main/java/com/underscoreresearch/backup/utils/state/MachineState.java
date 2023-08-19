package com.underscoreresearch.backup.utils.state;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.service.api.model.ReleaseFileItem;

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
        return 1;
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

            return Math.max(cpuLoad - processLoad, 0) * osBean.getAvailableProcessors();
        } catch (IllegalArgumentException exc) {
        }
        return Double.NaN;
    }

    public void waitForRunCheck() {
        if (pauseOnBattery) {
            if (occasionallyGetOnBattery() || occasionallyGetCpuUsage() > getMaxCpuUsage()) {
                synchronized (this) {
                    if (!loggedOnBattery) {
                        loggedOnBattery = true;
                        if (occasionallyGetOnBattery())
                            log.info("Pausing until power is restored");
                        else
                            log.info("Pausing until CPU usage goes down");
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
                } while (occasionallyGetOnBattery() || occasionallyGetCpuUsage() > getMaxCpuUsage());

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
        if (ret.isPresent())
            return ret.get();
        return null;
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
}
