package com.underscoreresearch.backup.utils;

import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.configuration.InstanceFactory;

@Slf4j
public class StateLogger {
    private static final String OLD_KEYWORD = " Old ";
    private static List<StatusLogger> loggers;

    public StateLogger() {
    }

    public void logDebug() {
        initialize();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage memHeapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        debug(() -> log.debug("Head memory {}/{} ({}%}", readableSize(memHeapUsage.getUsed()), readableSize(memHeapUsage.getMax()),
                memHeapUsage.getUsed() * 100 / memHeapUsage.getMax()));
        debug(() -> log.debug("Non heap memory {}/{} ({}%}", readableSize(nonHeapUsage.getUsed()), readableSize(nonHeapUsage.getCommitted()),
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

        if (!oldMemoryBean.isPresent() || !oldGcBean.isPresent() || !survivorMemoryBean.isPresent()) {
            log.error("Could not find old generation info");
            return;
        }

        long memoryAfterGCUse =
                (100 * (oldMemoryBean.get().getCollectionUsage().getUsed()
                        + survivorMemoryBean.get().getCollectionUsage().getUsed()))
                        / (Math.max(oldMemoryBean.get().getCollectionUsage().getMax(), 0)
                        + Math.max(survivorMemoryBean.get().getCollectionUsage().getMax(), 0));

        if (memoryAfterGCUse > 75) {
            log.warn("Heap memory after GC use {}% (Old gen collections {})", memoryAfterGCUse,
                    oldGcBean.get().getCollectionCount());
        } else {
            debug(() -> log.debug("Heap memory after GC use {}% (Old gen collections {})", memoryAfterGCUse,
                    oldGcBean.get().getCollectionCount()));
        }

        printLogStatus(true, (a) -> debug(() -> log.debug(a)));
    }

    public void logInfo() {
        printLogStatus(false, (a) -> log.info(a));
    }

    public void reset() {
        initialize();

        loggers.forEach(logger -> logger.resetStatus());
    }

    public List<StatusLine> logData(boolean temporal) {
        initialize();

        return loggers
                .stream()
                .filter(t -> temporal || !t.temporal())
                .map(log -> log.status())
                .flatMap(t -> t.stream())
                .collect(Collectors.toList());
    }

    private void printLogStatus(boolean temporal, Consumer<String> printer) {
        initialize();

        loggers
                .stream()
                .filter(t -> temporal || !t.temporal())
                .forEach(log -> log.status()
                        .forEach(item -> printer.accept(item.toString())));
    }

    private synchronized void initialize() {
        if (loggers == null) {
            loggers = InstanceFactory
                    .getReflections()
                    .getSubTypesOf(StatusLogger.class)
                    .stream().map(clz -> InstanceFactory.getInstance(clz))
                    .collect(Collectors.toList());
        }
    }
}
