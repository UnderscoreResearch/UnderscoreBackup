package com.underscoreresearch.backup.utils.state;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.changepoller.FileChangePoller;
import com.underscoreresearch.backup.file.changepoller.FsChangePoller;
import com.underscoreresearch.backup.service.api.model.ReleaseFileItem;
import com.underscoreresearch.backup.service.api.model.ReleaseResponse;
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
                        reason = "Paused until power is restored";
                    else
                        reason = "Paused until CPU usage goes down";

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
                            Thread.currentThread().interrupt();
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

    public boolean supportsAutomaticUpgrade() {
        return false;
    }

    public void upgrade(ReleaseResponse response) throws IOException {
        throw new UnsupportedOperationException("Automatic upgrade not supported on this platform");
    }

    public FileChangePoller createPoller() throws IOException {
        if (FsChangePoller.isSupported())
            return new FsChangePoller();
        throw new UnsupportedOperationException("Not supported on this platform");
    }

    protected void executeUpdateProcess(String[] cmd) throws IOException {
        log.info("Upgrading with command: \"{}\"", String.join(" ", cmd));
        Process process = Runtime.getRuntime().exec(cmd);
        new Thread(() -> {
            try {
                log.info("Update process exited with exit code {}", process.waitFor());
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "UpgradeExit").start();
        new Thread(() -> printOutput("error output", process.getErrorStream()), "UpdateError").start();
        new Thread(() -> printOutput("output", process.getInputStream()), "UpdateOutput").start();
    }

    private void printOutput(String name, InputStream errorStream) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        try {
            errorStream.transferTo(data);
        } catch (IOException ignored) {
        }
        String output = data.toString(StandardCharsets.UTF_8);
        if (!output.isBlank())
            log.warn("Update process {}:\n{}", name, output);
    }

    public void setOwnerOnlyPermissions(File file) throws IOException {
        HashSet<PosixFilePermission> set = new HashSet<PosixFilePermission>();

        set.add(PosixFilePermission.OWNER_READ);
        set.add(PosixFilePermission.OWNER_WRITE);
        if (file.isDirectory()) {
            set.add(PosixFilePermission.OWNER_EXECUTE);
        }

        Files.setPosixFilePermissions(file.toPath(), set);
    }
}
