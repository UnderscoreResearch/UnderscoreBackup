package com.underscoreresearch.backup.utils;

import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.configuration.InstanceFactory;

@Slf4j
public class StateLogger implements StatusLogger {
    private static final String OLD_KEYWORD = " Old ";
    private final AtomicLong lastHeapUsage = new AtomicLong();
    private final AtomicLong lastHeapUsageMax = new AtomicLong();
    private final AtomicLong lastMemoryAfterGCUse = new AtomicLong();
    private final AtomicLong lastGCCollectionCount = new AtomicLong();
    private final boolean debugMemory;
    private List<ManualStatusLogger> loggers;

    public StateLogger(boolean debugMemory) {
        this.debugMemory = debugMemory;
    }

    public static void addLogger(ManualStatusLogger logger) {
        if (InstanceFactory.isInitialized()) {
            StateLogger state = InstanceFactory.getInstance(StateLogger.class);
            state.initialize();
            synchronized (state.loggers) {
                state.loggers.add(logger);
            }
        }
    }

    public static void removeLogger(ManualStatusLogger logger) {
        if (InstanceFactory.isInitialized()) {
            StateLogger state = InstanceFactory.getInstance(StateLogger.class);
            state.initialize();
            synchronized (state.loggers) {
                state.loggers.remove(logger);
            }
        }
    }

    public void logDebug() {
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

        printLogStatus((type) -> type != Type.LOG, (a) -> debug(() -> log.debug(a)));
    }

    public void logInfo() {
        printLogStatus((type) -> type != Type.LOG, log::info);
    }

    public void reset() {
        initialize();

        synchronized (loggers) {
            loggers.stream().filter(t -> t.type() != Type.LOG).forEach(ManualStatusLogger::resetStatus);
        }
    }

    public List<StatusLine> logData(Function<Type, Boolean> filter) {
        initialize();

        List<ManualStatusLogger> currentLoggers;

        synchronized (loggers) {
            currentLoggers = loggers
                    .stream()
                    .filter(t -> filter.apply(t.type())).toList();
        }

        List<StatusLine> ret = currentLoggers.stream().map(ManualStatusLogger::status)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        currentLoggers.forEach(logger -> logger.filterItems(ret));

        return ret;
    }

    private void printLogStatus(Function<Type, Boolean> filter, Consumer<String> printer) {
        logData(filter).forEach(item -> printer.accept(item.toString()));
    }

    private synchronized void initialize() {
        if (loggers == null || loggers.size() == 0) {
            if (InstanceFactory.hasConfiguration(false)) {
                loggers = InstanceFactory
                        .getReflections()
                        .getSubTypesOf(StatusLogger.class)
                        .stream()
                        .filter(clz -> !Modifier.isAbstract(clz.getModifiers()))
                        .map(InstanceFactory::getInstance)
                        .collect(Collectors.toList());
            } else if (loggers == null) {
                loggers = new ArrayList<>();
            }
        }
    }

    @Override
    public void resetStatus() {
    }

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
                    lastMemoryAfterGCUse.get() + "%"));
            ret.add(new StatusLine(getClass(), "HEAP_FULL_GC", "Old generation GC count", lastGCCollectionCount.get()));
        }
        if (lastMemoryAfterGCUse.get() > 90) {
            ret.add(new StatusLine(getClass(), "MEMORY_HIGH", "Memory usage high", lastMemoryAfterGCUse.get(),
                    lastMemoryAfterGCUse.get() + "%"));
        }
        return ret;
    }
}
