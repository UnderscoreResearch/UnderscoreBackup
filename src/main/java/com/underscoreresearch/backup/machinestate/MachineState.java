package com.underscoreresearch.backup.machinestate;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.changepoller.FileChangePoller;
import com.underscoreresearch.backup.file.changepoller.FsChangePoller;
import com.underscoreresearch.backup.service.api.model.ReleaseFileItem;
import com.underscoreresearch.backup.service.api.model.ReleaseResponse;
import com.underscoreresearch.backup.utils.log.PausedStatusLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static com.underscoreresearch.backup.io.IOUtils.executeProcess;

/**
 * Abstract base class for platform-specific machine state monitoring.
 * Provides functionality to monitor system state such as battery status, CPU usage,
 * and handles pausing operations when the system is in a state not suitable for backups.
 * Platform-specific implementations extend this class to provide OS-specific behavior.
 */
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

    /**
     * Returns the maximum CPU usage threshold for backup operations.
     * Operations may be paused if CPU usage exceeds this threshold.
     *
     * @return The maximum CPU usage as a fraction (0.0-1.0)
     */
    protected double getMaxCpuUsage() {
        return 0.2;
    }

    /**
     * Checks if the system is currently running on battery power.
     * This method is overridden by platform-specific implementations.
     *
     * @return True if the system is running on battery, false otherwise
     */
    public boolean getOnBattery() {
        return false;
    }

    /**
     * Gets the current CPU usage of the system, excluding the backup process itself.
     *
     * @return The CPU usage as a fraction (0.0-1.0), or NaN if not available
     */
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

    /**
     * Checks system conditions and pauses operations if necessary.
     * Operations are paused when the system is on battery or CPU usage is high,
     * and resumed when conditions return to normal.
     */
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

    /**
     * Selects the appropriate distribution file for the current platform.
     *
     * @param files List of available release files
     * @return The selected distribution file, or null if none is suitable
     */
    public ReleaseFileItem getDistribution(List<ReleaseFileItem> files) {
        Optional<ReleaseFileItem> ret = files.stream().filter(file -> file.getName().endsWith(".tar")).findAny();
        if (ret.isPresent())
            return ret.get();
        ret = files.stream().filter(file -> file.getName().endsWith(".zip")).findAny();
        return ret.orElse(null);
    }

    /**
     * Sets the process to run at a lower priority.
     * This method is overridden by platform-specific implementations.
     */
    public void lowPriority() {
    }

    /**
     * Gets the CPU usage, caching the result for a minimum duration to avoid frequent checks.
     *
     * @return The cached CPU usage as a fraction (0.0-1.0)
     */
    private synchronized double occasionallyGetCpuUsage() {
        if (nextCpuCheck.isBefore(Instant.now())) {
            lastCpuUsage = getCpuUsage();
            nextCpuCheck = Instant.now().plus(MINIMUM_WAIT);
        }

        return lastCpuUsage;
    }

    /**
     * Checks if the system is on battery, caching the result for a minimum duration to avoid frequent checks.
     *
     * @return The cached battery status
     */
    private synchronized boolean occasionallyGetOnBattery() {
        if (nextCheck.isBefore(Instant.now())) {
            lastValue = getOnBattery();
            nextCheck = Instant.now().plus(MINIMUM_WAIT);
        }

        return lastValue;
    }

    /**
     * Checks if automatic upgrades are supported on this platform.
     *
     * @return True if automatic upgrades are supported, false otherwise
     */
    public boolean supportsAutomaticUpgrade() {
        return false;
    }

    /**
     * Performs an automatic upgrade using the provided release information.
     * This method is overridden by platform-specific implementations that support upgrades.
     *
     * @param response The release response containing upgrade information
     * @throws IOException If an error occurs during the upgrade
     */
    public void upgrade(ReleaseResponse response) throws IOException {
        throw new UnsupportedOperationException("Automatic upgrade not supported on this platform");
    }

    /**
     * Creates a file change poller appropriate for the current platform.
     *
     * @return A FileChangePoller instance
     * @throws IOException If an error occurs creating the poller
     */
    public FileChangePoller createPoller() throws IOException {
        if (FsChangePoller.isSupported())
            return new FsChangePoller();
        throw new UnsupportedOperationException("Not supported on this platform");
    }

    /**
     * Executes an update process with the specified command.
     *
     * @param cmd The command to execute
     * @throws IOException If an error occurs executing the process
     */
    protected void executeUpdateProcess(String[] cmd) throws IOException {
        executeProcess("Update", cmd);
    }

    /**
     * Sets file permissions to be accessible only by the owner.
     *
     * @param file The file to modify permissions for
     * @throws IOException If an error occurs setting the permissions
     */
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
