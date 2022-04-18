package com.underscoreresearch.backup.file.implementation;

import static com.underscoreresearch.backup.utils.LogUtil.formatTimestamp;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.FileScanner;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.ScannerScheduler;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.RepositoryTrimmer;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupPendingSet;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.utils.StateLogger;
import com.underscoreresearch.backup.utils.StatusLine;
import com.underscoreresearch.backup.utils.StatusLogger;

@Slf4j
public class ScannerSchedulerImpl implements ScannerScheduler, StatusLogger {
    private static final long SLEEP_DELAY_MS = 60 * 1000;
    private static final long SLEEP_RESUME_DELAY_MS = 5 * 60 * 1000;
    private final BackupConfiguration configuration;
    private final FileScanner scanner;
    private final MetadataRepository repository;
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder().setNameFormat(getClass().getSimpleName() + "-%d").build());
    private final RepositoryTrimmer trimmer;
    private final StateLogger stateLogger;
    private final boolean[] pendingSets;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Map<String, Date> scheduledTimes = new HashMap<>();
    private boolean shutdown;
    private boolean scheduledRestart;
    private boolean pending;

    public ScannerSchedulerImpl(BackupConfiguration configuration,
                                MetadataRepository repository,
                                RepositoryTrimmer trimmer,
                                FileScanner scanner,
                                StateLogger stateLogger) {
        this.configuration = configuration;
        this.scanner = scanner;
        this.repository = repository;
        this.trimmer = trimmer;
        this.stateLogger = stateLogger;

        pendingSets = new boolean[configuration.getSets().size()];

        executor.scheduleAtFixedRate(this::detectSleep, SLEEP_DELAY_MS, SLEEP_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void detectSleep() {
        synchronized (scheduledTimes) {
            Date expired = new Date(Instant.now().minusMillis(SLEEP_RESUME_DELAY_MS).toEpochMilli());
            Set<String> expiredSets = new HashSet<>();
            for (Map.Entry<String, Date> entry : scheduledTimes.entrySet()) {
                if (entry.getValue().before(expired)) {
                    expiredSets.add(entry.getKey());
                }
            }
            int index = 0;
            for (BackupSet set : configuration.getSets()) {
                if (expiredSets.contains(set.getId())) {
                    log.info("Rescheduling set {} after sleep", set.getId());
                    restartSet(set, index);
                    scheduleNext(set, index);
                }
                index++;
            }
        }
    }

    @Override
    public void start() {
        boolean hasSchedules = initializeScheduler();

        lock.lock();
        while (!shutdown) {
            int i = 0;
            while (i < pendingSets.length && !shutdown) {
                if (pendingSets[i]) {
                    lock.unlock();
                    BackupSet set = configuration.getSets().get(i);
                    try {
                        log.info("Started scanning {} for {}", set.getAllRoots(), set.getId());
                        if (scanner.startScanning(set)) {
                            synchronized (scheduledTimes) {
                                Date date = scheduledTimes.get(set.getId());
                                if (date != null && date.after(new Date())) {
                                    try {
                                        repository.addPendingSets(new BackupPendingSet(set.getId(), set.getSchedule(), date));
                                    } catch (IOException e) {
                                        log.warn("Failed saving next scheduled time for backup set {}", set.getId());
                                    }
                                }
                            }
                            pendingSets[i] = false;
                            i++;
                            if (set.getRetention() != null) {
                                trimmer.trimRepository();
                            }
                        } else {
                            while (!shutdown && !scheduledRestart && !IOUtils.hasInternet()) {
                                Thread.sleep(1000);
                            }
                            i = 0;
                        }
                        scheduledRestart = false;
                    } catch (Exception exc) {
                        log.error("Failed processing set " + set.getId(), exc);
                    }
                    lock.lock();
                } else {
                    i++;
                }
            }

            if (!shutdown) {
                if (!hasSchedules) {
                    break;
                }
                try {
                    log.info("Pausing for next scheduled scan");
                    try {
                        InstanceFactory.getInstance(ManifestManager.class).flushLog();
                        InstanceFactory.getInstance(StateLogger.class).reset();
                        repository.close();
                    } catch (IOException e) {
                        log.error("Failed to close repository before waiting", e);
                    }
                    System.gc();
                    pending = true;
                    condition.await();
                } catch (InterruptedException e) {
                    log.error("Failed to wait", e);
                }
                pending = false;
            }
        }
        lock.unlock();
        stateLogger.reset();
    }

    private boolean initializeScheduler() {
        boolean hasSchedules = false;
        lock.lock();

        try {
            try {
                repository.addDirectory(new BackupDirectory("",
                        Instant.now().toEpochMilli(),
                        configuration.getSets().stream().flatMap(t -> t.getRoots().stream()
                                        .map(s -> s.getNormalizedPath()))
                                .collect(Collectors.toCollection(TreeSet::new))));
            } catch (IOException e) {
                log.error("Failed to register root of backup sets in repository", e);
            }

            Map<String, BackupPendingSet> pendingScheduled = new HashMap<>();
            try {
                repository.getPendingSets().forEach((pending) -> {
                    pendingScheduled.put(pending.getSetId(), pending);
                });
            } catch (IOException e) {
                log.warn("Failed to read scheduled sets");
            }

            for (int i = 0; i < configuration.getSets().size(); i++) {
                BackupSet set = configuration.getSets().get(i);
                BackupPendingSet pendingSet = pendingScheduled.remove(set.getId());
                if (pendingSet != null
                        && pendingSet.getSchedule().equals(set.getSchedule())
                        && pendingSet.getScheduledAt().after(new Date())) {
                    hasSchedules = true;
                    scheduleNextAt(set, i, pendingSet.getScheduledAt());
                } else {
                    deletePendingSet(set.getId());
                    pendingSets[i] = true;
                    if (!Strings.isNullOrEmpty(set.getSchedule())) {
                        hasSchedules = true;
                        scheduleNext(set, i);
                    }
                }
            }

            for (String setId : pendingScheduled.keySet()) {
                deletePendingSet(setId);
            }
        } finally {
            lock.unlock();
        }
        return hasSchedules;
    }

    private void deletePendingSet(String id) {
        try {
            repository.deletePendingSets(id);
        } catch (IOException e) {
            log.error("Failed saving next scheduled time for backup set {}", id);
        }
    }

    private void scheduleNext(BackupSet set, int index) {
        try {
            CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
            Cron expression = parser.parse(set.getSchedule()).validate();

            ZonedDateTime now = ZonedDateTime.now();
            ExecutionTime scheduler = ExecutionTime.forCron(expression);
            Optional<ZonedDateTime> nextExecution = scheduler.nextExecution(now);
            if (nextExecution.isPresent()) {
                Date date = Date.from(nextExecution.get().toInstant());
                ;
                if (date.getTime() - new Date().getTime() > 0) {
                    scheduleNextAt(set, index, date);
                }
            } else {
                synchronized (scheduledTimes) {
                    scheduledTimes.remove(set.getId());
                }
                deletePendingSet(set.getId());
                log.warn("No new time to schedule set");
            }

        } catch (IllegalArgumentException e) {
            log.error("Invalid cron expression, will not reschedule after initial run: {}",
                    set.getSchedule(), e);
        }
    }

    private void scheduleNextAt(BackupSet set, int index, Date date) {
        log.info("Schedule set {} to run again at {}", set.getId(), formatTimestamp(date.getTime()));
        synchronized (scheduledTimes) {
            scheduledTimes.put(set.getId(), date);
        }
        long delta = date.getTime() - new Date().getTime();
        if (delta < 1) {
            delta = 1;
        }
        executor.schedule(() -> restartSet(set, index),
                delta,
                TimeUnit.MILLISECONDS);
    }

    private void restartSet(BackupSet set, int index) {
        lock.lock();
        try {
            if (!pendingSets[index]) {
                log.info("Restarting scan of {} for {}", set.getAllRoots(), set.getId());
                scheduledRestart = true;
                pendingSets[index] = true;
                scanner.shutdown();
                condition.signal();
            } else {
                log.info("Set {} not completed, so not restarting it", set.getAllRoots(), set.getId());
            }
            scheduleNext(set, index);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void shutdown() {
        lock.lock();
        shutdown = true;
        executor.shutdownNow();
        scanner.shutdown();
        condition.signal();
        lock.unlock();
    }

    @Override
    public void resetStatus() {

    }

    @Override
    public List<StatusLine> status() {
        if (!pending) {
            return new ArrayList<>();
        }
        synchronized (scheduledTimes) {
            return scheduledTimes.entrySet().stream().map(item ->
                    new StatusLine(getClass(), "SCHEDULED_BACKUP_" + item.getKey(),
                            String.format("Scheduled next run of set %d at %s",
                                    indexOfSet(item.getKey()),
                                    formatTimestamp(item.getValue().getTime())))).collect(Collectors.toList());
        }
    }

    private int indexOfSet(String key) {
        for (int i = 0; i < configuration.getSets().size(); i++) {
            if (configuration.getSets().get(i).getId().equals(key)) {
                return i + 1;
            }
        }
        return -1;
    }
}
