package com.underscoreresearch.backup.utils.log;

import com.underscoreresearch.backup.ui.desktop.UIHandler;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.machinestate.MachineState;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.utils.log.LogUtil.debug;
import static com.underscoreresearch.backup.utils.log.LogUtil.readableSize;

/**
 * A central logger for system state information, including memory usage, deadlock detection,
 * and status from various components of the backup system.
 * This class monitors the system state and can detect potential deadlocks or inactivity.
 */
@Slf4j
public class StateLogger implements StatusLogger {
    // Bumping this because we want it to not trigger specifically for internet brownouts.
    public static final Duration INACTVITY_DURATION = Duration.ofMinutes(30);
    private static final Duration MAX_DEADLOCK_DURATION = Duration.ofSeconds(70);
    private static final String OLD_KEYWORD = " Old ";
    private static final AtomicInteger loggingDebug = new AtomicInteger();
    private final AtomicLong lastHeapUsage = new AtomicLong();
    private final AtomicLong lastHeapUsageMax = new AtomicLong();
    private final AtomicLong lastMemoryAfterGCUse = new AtomicLong();
    private final AtomicLong lastGCCollectionCount = new AtomicLong();
    private final boolean debugMemory;
    private final List<ManualStatusLogger> additionalLoggers = new ArrayList<>();
    private List<ManualStatusLogger> loggers;
    private String activityHash;
    private Instant lastActivityHash;
    private Instant lastDeadlockCheck;

    /**
     * Creates a new StateLogger with the specified debug memory flag.
     *
     * @param debugMemory Whether to include detailed memory information in status reports
     */
    public StateLogger(boolean debugMemory) {
        this.debugMemory = debugMemory;
    }

    /**
     * Adds a status logger to the list of additional loggers.
     *
     * @param logger The logger to add
     */
    public static void addLogger(ManualStatusLogger logger) {
        if (InstanceFactory.isInitialized()) {
            StateLogger state = InstanceFactory.getInstance(StateLogger.class);
            synchronized (state.additionalLoggers) {
                state.additionalLoggers.add(logger);
            }
        }
    }

    /**
     * Removes a status logger from the list of additional loggers.
     *
     * @param logger The logger to remove
     */
    public static void removeLogger(ManualStatusLogger logger) {
        if (InstanceFactory.isInitialized()) {
            StateLogger state = InstanceFactory.getInstance(StateLogger.class);
            synchronized (state.additionalLoggers) {
                state.additionalLoggers.remove(logger);
            }
        }
    }

    /**
     * Logs debug information about the current system state.
     * This method is designed to be called periodically to monitor the system.
     */
    public static void logDebug() {
        try {
            if (loggingDebug.getAndIncrement() == 0) {
                try {
                    StateLogger state = InstanceFactory.getInstance(StateLogger.class);
                    state.logDebugInternal();
                } finally {
                    loggingDebug.set(0);
                }
            } else if (loggingDebug.get() == 5) {
                logDeadlock();
            }
        } catch (Throwable exc) {
            log.error("Failed watchdog", exc);
        }
    }

    /**
     * Logs information about a detected deadlock, including stack traces.
     */
    private static void logDeadlock() {
        String message = UIHandler.getActiveTaskMessage();
        StringBuilder sb = new StringBuilder(
                "Detected potential deadlock: " + (message != null ? message : "Not active"));
        LogUtil.dumpAllStackTrace(sb);
        log.error(sb.toString());
    }

    /**
     * Internal implementation of debug logging, collecting and logging memory usage
     * and other system state information.
     */
    public void logDebugInternal() {
        initialize();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage memHeapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        debug(() -> log.debug("Non heap memory {} / {} ({}%}", readableSize(nonHeapUsage.getUsed()), readableSize(nonHeapUsage.getCommitted()),
                nonHeapUsage.getUsed() * 100 / nonHeapUsage.getCommitted()));

        final Optional<MemoryPoolMXBean> oldMemoryBean = ManagementFactory.getMemoryPoolMXBeans()
                .stream()
                .filter(t -> t.getName().contains(OLD_KEYWORD))
                .findAny();

        final Optional<GarbageCollectorMXBean> oldGcBean = ManagementFactory
                .getGarbageCollectorMXBeans()
                .stream()
                .filter(t -> t.getName().contains(OLD_KEYWORD) || t.getName().contains(" MarkSweep"))
                .findAny();

        Optional<MemoryPoolMXBean> survivorMemoryBean = ManagementFactory.getMemoryPoolMXBeans()
                .stream()
                .filter(t -> t.getName().contains(" Survivor "))
                .findAny();

        if (oldMemoryBean.isEmpty() || oldGcBean.isEmpty() || survivorMemoryBean.isEmpty()) {
            log.error("Could not find old generation info");
            return;
        }

        long memoryAfterGCUse =
                (100 * (oldMemoryBean.get().getCollectionUsage().getUsed()
                        + survivorMemoryBean.get().getCollectionUsage().getUsed()))
                        / (Math.max(oldMemoryBean.get().getCollectionUsage().getMax(), 0)
                        + Math.max(survivorMemoryBean.get().getCollectionUsage().getMax(), 0));
        lastHeapUsage.set(memHeapUsage.getUsed());
        lastHeapUsageMax.set(memHeapUsage.getMax());
        lastMemoryAfterGCUse.set(memoryAfterGCUse);
        lastGCCollectionCount.set(oldGcBean.get().getCollectionCount());

        if (memoryAfterGCUse > 75) {
            log.warn("Heap memory after GC use {}% (Old gen collections {})", memoryAfterGCUse,
                    oldGcBean.get().getCollectionCount());
        }

        debug(() -> {
            MachineState state = InstanceFactory.getInstance(MachineState.class);
            log.debug("Current CPU usage {}", state.getCpuUsage());
        });

        if (InstanceFactory.hasConfiguration(false)) {
            detectDeadlock();

            debug(() -> printLogStatus((type) -> type != Type.LOG, log::debug));
        }
    }

    /**
     * Detects potential deadlocks by monitoring thread activity and system progress.
     * If a deadlock is detected, logs detailed information about the system state.
     */
    private void detectDeadlock() {
        Instant now = Instant.now();

        String newActivityHash = null;
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        long[] threadIds = bean.findDeadlockedThreads(); // Returns null if no threads are deadlocked.

        if (threadIds != null) {
            StringBuilder sb = new StringBuilder();
            for (long threadId : threadIds) {
                sb.append(threadId).append(" ");
            }
            newActivityHash = Hash.hash(sb.toString().getBytes(StandardCharsets.UTF_8));
        } else {
            boolean isActive = UIHandler.isLongActive();

            debug(() -> log.debug(isActive ? "Currently active" : "Not active"));
            // We only do this if we are not sleeping (IE we get a ping here at least once a minute)

            if (lastDeadlockCheck != null && lastDeadlockCheck.plus(MAX_DEADLOCK_DURATION).isAfter(now) && isActive) {
                List<StatusLine> logItems = getDeadlockLogItems();
                try {
                    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                        try (DataOutputStream writer = new DataOutputStream(out)) {
                            logItems.forEach(item -> {
                                if (includeProgressItem(item)) {
                                    try {
                                        writer.write(item.getCode().getBytes(StandardCharsets.UTF_8));
                                        if (item.getValue() != null) {
                                            writer.writeLong(item.getValue());
                                        } else {
                                            writer.writeUTF(item.getValueString());
                                        }
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                                debug(() -> log.debug(item.toString()));
                            });
                        }
                        newActivityHash = Hash.hash(out.toByteArray());
                    }
                } catch (Throwable e) {
                    log.error("Failed calculating progress", e);
                }
            } else {
                activityHash = null;
            }

            lastDeadlockCheck = Instant.now();
        }

        if (newActivityHash != null) {
            if (newActivityHash.equals(activityHash)) {
                if (threadIds != null || lastActivityHash.plus(INACTVITY_DURATION).isBefore(Instant.now())) {
                    logDeadlock();
                    if (!LogUtil.isDebug()) {
                        getDeadlockLogItems().forEach(item -> log.info(item.toString()));
                    }
                    activityHash = "";
                }
            } else if (!"".equals(activityHash)) {
                activityHash = newActivityHash;
                lastActivityHash = Instant.now();
            }
        }
    }

    /**
     * Gets the status lines to include in deadlock detection.
     *
     * @return A list of status lines for deadlock detection
     */
    private List<StatusLine> getDeadlockLogItems() {
        return InstanceFactory.hasConfiguration(true) ?
                logData((type) -> type != Type.LOG).stream().filter(this::includeProgressItem).toList() :
                new ArrayList<>();
    }

    /**
     * Determines whether a status line should be included in progress tracking for deadlock detection.
     *
     * @param item The status line to check
     * @return True if the item should be included, false otherwise
     */
    private boolean includeProgressItem(StatusLine item) {
        if (item.getCode().endsWith("_DURATION") ||
                item.getCode().endsWith("_THROUGHPUT") ||
                item.getCode().startsWith("SCHEDULED_BACKUP_")) {
            return false;
        }

        return switch (item.getCode()) {
            case "HEAP_MEMORY",
                 "HEAP_AFTER_GC",
                 "HEAP_FULL_GC",
                 "MEMORY_HIGH",
                 "REPOSITORY_INFO_TIMESTAMP" -> false;
            default -> item.getValue() != null || item.getValueString() != null;
        };
    }

    /**
     * Logs the current system state at INFO level.
     */
    public void logInfo() {
        try {
            printLogStatus((type) -> type != Type.LOG, log::info);
        } catch (Exception exc) {
            log.error("Failed logging status", exc);
        }
    }

    /**
     * Resets the status of all non-LOG type loggers.
     */
    public void reset() {
        initialize();

        synchronized (this) {
            loggers.stream().filter(t -> t.type() != Type.LOG).forEach(ManualStatusLogger::resetStatus);
        }
        synchronized (additionalLoggers) {
            additionalLoggers.stream().filter(t -> t.type() != Type.LOG).forEach(ManualStatusLogger::resetStatus);
        }
    }

    /**
     * Collects status data from all loggers that match the specified filter.
     *
     * @param filter A function that determines which logger types to include
     * @return A list of status lines from the matching loggers
     */
    public List<StatusLine> logData(Function<Type, Boolean> filter) {
        initialize();

        List<ManualStatusLogger> currentLoggers;

        synchronized (this) {
            currentLoggers = loggers
                    .stream()
                    .filter(t -> filter.apply(t.type())).collect(Collectors.toList());
        }

        synchronized (additionalLoggers) {
            currentLoggers.addAll(additionalLoggers
                    .stream()
                    .filter(t -> filter.apply(t.type())).toList());
        }

        List<StatusLine> ret = currentLoggers.stream().map(ManualStatusLogger::status)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        currentLoggers.forEach(logger -> logger.filterItems(ret));

        return ret;
    }

    /**
     * Prints status information from loggers that match the specified filter.
     *
     * @param filter A function that determines which logger types to include
     * @param printer A consumer that handles the formatted status lines
     */
    private void printLogStatus(Function<Type, Boolean> filter, Consumer<String> printer) {
        logData(filter).forEach(item -> printer.accept(item.toString()));
    }

    /**
     * Initializes the list of status loggers if it hasn't been initialized yet.
     */
    private void initialize() {
        synchronized (this) {
            if (loggers == null || loggers.isEmpty()) {
                if (InstanceFactory.hasConfiguration(false)) {
                    loggers = InstanceFactory
                            .getReflections()
                            .getSubTypesOf(StatusLogger.class)
                            .stream()
                            .filter(clz -> !Modifier.isAbstract(clz.getModifiers()))
                            .map((clz) -> {
                                try {
                                    return InstanceFactory.getInstance(clz);
                                } catch (Exception e) {
                                    debug(() -> log.warn("Failed instantiating logger {}", clz.getTypeName(), e));
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                } else if (loggers == null) {
                    loggers = new ArrayList<>();
                }
            }
        }
    }

    /**
     * No-op implementation as the state logger's status is not reset.
     */
    @Override
    public void resetStatus() {
    }

    /**
     * Returns the current memory usage status if debug memory is enabled.
     *
     * @return A list of status lines with memory usage information
     */
    @Override
    public List<StatusLine> status() {
        List<StatusLine> ret = new ArrayList<>();
        if (debugMemory && lastHeapUsage.get() > 0) {
            ret.add(new StatusLine(getClass(), "HEAP_MEMORY", "Heap memory usage",
                    lastHeapUsage.get(),
                    lastHeapUsageMax.get(),
                    readableSize(lastHeapUsage.get()) + " / " + readableSize(lastHeapUsageMax.get()) + " (" +
                            lastHeapUsage.get() * 100 / lastHeapUsageMax.get() + "%)"));
            ret.add(new StatusLine(getClass(), "HEAP_AFTER_GC", "Heap memory usage after GC", lastMemoryAfterGCUse.get(),
                    100L,
                    lastMemoryAfterGCUse.get() + "%"));
            ret.add(new StatusLine(getClass(), "HEAP_FULL_GC", "Old generation GC count", lastGCCollectionCount.get()));
        }
        if (lastMemoryAfterGCUse.get() > 90) {
            ret.add(new StatusLine(getClass(), "MEMORY_HIGH", "Memory usage high", lastMemoryAfterGCUse.get(),
                    100L,
                    lastMemoryAfterGCUse.get() + "%"));
        }
        return ret;
    }
}
