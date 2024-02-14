package com.underscoreresearch.backup.file.implementation;

import static com.underscoreresearch.backup.utils.LogUtil.formatTimestamp;

import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
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
import com.underscoreresearch.backup.cli.helpers.BlockValidator;
import com.underscoreresearch.backup.cli.helpers.RepositoryTrimmer;
import com.underscoreresearch.backup.cli.ui.UIHandler;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.ContinuousBackup;
import com.underscoreresearch.backup.file.FileChangeWatcher;
import com.underscoreresearch.backup.file.FileScanner;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.ScannerScheduler;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupPendingSet;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.model.BackupSetRoot;
import com.underscoreresearch.backup.service.api.model.ReleaseResponse;
import com.underscoreresearch.backup.utils.StateLogger;
import com.underscoreresearch.backup.utils.state.MachineState;

@Slf4j
public class ScannerSchedulerImpl implements ScannerScheduler {
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
    private final Random random = new Random();
    private final FileChangeWatcher fileChangeWatcher;
    private final ContinuousBackup continuousBackup;
    private final BackupStatsLogger backupStatsLogger;
    private final boolean checkVersion;
    private boolean shutdown;
    private boolean scheduledRestart;
    private boolean running;

    public ScannerSchedulerImpl(BackupConfiguration configuration,
                                MetadataRepository repository,
                                RepositoryTrimmer trimmer,
                                FileScanner scanner,
                                StateLogger stateLogger,
                                FileChangeWatcher fileChangeWatcher,
                                ContinuousBackup continuousBackup,
                                BackupStatsLogger backupStatsLogger,
                                boolean checkVersion) {
        this.configuration = configuration;
        this.scanner = scanner;
        this.repository = repository;
        this.trimmer = trimmer;
        this.stateLogger = stateLogger;
        this.fileChangeWatcher = fileChangeWatcher;
        this.continuousBackup = continuousBackup;
        this.backupStatsLogger = backupStatsLogger;
        this.checkVersion = checkVersion;

        pendingSets = new boolean[configuration.getSets().size()];

        executor.scheduleAtFixedRate(this::detectSleep, SLEEP_DELAY_MS, SLEEP_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    public static void updateOptimizeSchedule(MetadataRepository copyRepository,
                                              String schedule) throws IOException {
        if (schedule != null) {
            Date date = getNextScheduleDate(schedule);
            if (date != null) {
                BackupPendingSet pendingSet = BackupPendingSet.builder()
                        .setId("")
                        .scheduledAt(date)
                        .schedule(schedule)
                        .build();
                copyRepository.addPendingSets(pendingSet);
            }
        }
    }

    public static void updateTrimSchedule(MetadataRepository repository,
                                          String schedule) throws IOException {
        if (schedule != null) {
            Date date = getNextScheduleDate(schedule);
            if (date != null) {
                BackupPendingSet pendingSet = BackupPendingSet.builder()
                        .setId("=")
                        .scheduledAt(date)
                        .schedule(schedule)
                        .build();
                repository.addPendingSets(pendingSet);
            }
        } else {
            repository.deletePendingSets("=");
        }
    }

    private static Date getNextScheduleDate(String schedule) {
        if (schedule != null) {
            try {
                CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
                Cron expression = parser.parse(schedule).validate();

                ZonedDateTime now = ZonedDateTime.now();
                ExecutionTime scheduler = ExecutionTime.forCron(expression);
                Optional<ZonedDateTime> nextExecution = scheduler.nextExecution(now);
                if (nextExecution.isPresent()) {
                    Date date = Date.from(nextExecution.get().toInstant());

                    if (date.getTime() - new Date().getTime() > 0) {
                        return date;
                    }
                } else {
                    log.warn("No new time to schedule");
                }
            } catch (IllegalArgumentException e) {
                log.error("Invalid cron expression, will not reschedule after initial run: {}",
                        schedule, e);
            }
        }
        return null;
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
                    log.info("Rescheduling set \"{}\" after sleep", set.getId());
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

        updateOptimizeSchedule();

        try {
            repository.upgradeStorage();
        } catch (IOException e) {
            log.error("Failed to upgrade repository storage", e);
        }

        lock.lock();
        running = true;
        backupStatsLogger.setUploadRunning(true);
        try {
            fileChangeWatcher.start();
        } catch (IOException | UnsupportedOperationException e) {
            log.warn("Failed to start watching for filesystem changes", e);
        }
        while (!shutdown) {
            int i = 0;
            boolean anyRan = false;
            while (i < pendingSets.length && !shutdown) {
                if (pendingSets[i]) {
                    lock.unlock();
                    BackupSet set = configuration.getSets().get(i);
                    try {
                        String message = String.format("Started scanning %s for %s", set.getAllRoots(), set.getId());
                        log.info(message);
                        UIHandler.displayInfoMessage(message);
                        try (Closeable ignored = UIHandler.registerTask("Backing up " + set.getId(), true)) {
                            if (scanner.startScanning(set)) {
                                anyRan = true;
                                rescheduleCompletedSet(i, set);
                                if (set.getRetention() != null) {
                                    boolean fileOnly = shouldOnlyDoFileTrim();
                                    RepositoryTrimmer.Statistics statistics = trimmer.trimRepository(fileOnly);
                                    backupStatsLogger.updateStats(statistics, !fileOnly);
                                    trimmer.resetStatus();
                                }
                                i++;
                            } else {
                                while (!shutdown && !scheduledRestart && !IOUtils.hasInternet()) {
                                    Thread.sleep(1000);
                                }
                                i = 0;
                            }
                        }
                        scheduledRestart = false;
                    } catch (Exception exc) {
                        log.error("Failed processing set \"{}\"", set.getId(), exc);
                        rescheduleCompletedSet(i, set);
                        i++;
                    }
                    lock.lock();
                } else {
                    i++;
                }
            }

            stateLogger.logInfo();

            if (!shutdown) {
                try {
                    try {
                        if (anyRan) {
                            backupCompletedCleanup();
                            UIHandler.displayInfoMessage("Backup completed");
                        }
                        repository.close();
                    } catch (IOException e) {
                        log.error("Failed to close repository before waiting", e);
                    }
                    if (!hasSchedules) {
                        break;
                    }
                    try {
                        InstanceFactory.getInstance(ManifestManager.class).syncLog();
                    } catch (IOException e) {
                        log.error("Failed to flush backup log", e);
                    }

                    checkNewVersion();

                    Closeable task;
                    if (fileChangeWatcher.active()) {
                        log.info("Waiting for filesystem changes");
                        task = UIHandler.registerTask("Waiting for filesystem changes", false);
                    } else {
                        log.info("Paused for next scheduled scan");
                        task = UIHandler.registerTask("Paused for next scheduled scan", false);
                    }
                    try {
                        continuousBackup.start();
                        System.gc();
                        backupStatsLogger.setUploadRunning(false);
                        condition.await();
                        continuousBackup.shutdown();
                    } finally {
                        try {
                            task.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } catch (InterruptedException e) {
                    log.error("Failed to wait", e);
                }
                backupStatsLogger.setUploadRunning(true);
            }
        }
        try {
            fileChangeWatcher.stop();
        } catch (IOException e) {
            log.error("Failed to stop watching for filesystem changes", e);
        }
        running = false;
        backupStatsLogger.setUploadRunning(false);
        condition.signal();
        lock.unlock();

        checkNewVersion();
    }

    private void rescheduleCompletedSet(int i, BackupSet set) {
        synchronized (scheduledTimes) {
            Date date = scheduledTimes.get(set.getId());
            if (date == null || date.after(new Date())) {
                try {
                    repository.addPendingSets(new BackupPendingSet(set.getId(), set.getSchedule(), date));
                } catch (IOException e) {
                    log.warn("Failed saving next scheduled time for backup set \"{}\"", set.getId());
                }
            }
        }
        pendingSets[i] = false;
    }

    private void checkNewVersion() {
        if (checkVersion && !shutdown) {
            if (configuration.getManifest().getVersionCheck() == null || configuration.getManifest().getVersionCheck()) {
                ReleaseResponse version = InstanceFactory.getInstance(ServiceManager.class).checkVersion();
                if (version != null) {
                    UIHandler.displayInfoMessage(String.format("New version %s available:\n\n%s",
                            version.getVersion(), version.getName()));
                    if (configuration.getManifest().getAutomaticUpgrade() == null || configuration.getManifest().getAutomaticUpgrade()) {
                        MachineState state = InstanceFactory.getInstance(MachineState.class);
                        if (state.supportsAutomaticUpgrade()) {
                            try {
                                log.info("Upgrading to version {}", version.getVersion());
                                state.upgrade(version);
                            } catch (IOException e) {
                                log.warn("Failed to upgrade to version {}", version.getVersion(), e);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean shouldOnlyDoFileTrim() {
        for (boolean set : pendingSets) {
            if (set) {
                return true;
            }
        }

        if (!Strings.isNullOrEmpty(configuration.getManifest().getTrimSchedule())) {
            try {
                BackupPendingSet pendingSet = getTrimSchedulePendingSet();
                if (pendingSet != null) {
                    if (pendingSet.getScheduledAt() != null && pendingSet.getScheduledAt().before(new Date())) {
                        return false;
                    } else
                        return Objects.equals(pendingSet.getSchedule(),
                                configuration.getManifest().getTrimSchedule());
                }
            } catch (IOException exc) {
                log.error("Failed to get pending set for trim", exc);
            }
        }

        return false;
    }

    private void updateOptimizeSchedule() {
        try {
            BackupPendingSet backupPendingSet = getOptimizeSchedulePendingSet();
            if (backupPendingSet == null || !Objects.equals(backupPendingSet.getSchedule(),
                    configuration.getManifest().getOptimizeSchedule())) {
                updateOptimizeSchedule(repository, configuration.getManifest().getOptimizeSchedule());
            }
        } catch (IOException exc) {
            log.error("Failed to check for optimize repository schedule", exc);
        }
    }

    private void backupCompletedCleanup() throws IOException {
        InstanceFactory.getInstance(ManifestManager.class).syncLog();
        stateLogger.reset();
        if (repository.isErrorsDetected()) {
            log.warn("Skipping log optimization and block validation due to errors");
        } else {
            BackupPendingSet pendingSet = getOptimizeSchedulePendingSet();
            if (pendingSet != null
                    && pendingSet.getScheduledAt() != null
                    && pendingSet.getScheduledAt().before(new Date())) {
                InstanceFactory.getInstance(ManifestManager.class)
                        .optimizeLog(repository, InstanceFactory.getInstance(LogConsumer.class), false);
                stateLogger.reset();
                if (!repository.isErrorsDetected()) {
                    InstanceFactory.getInstance(BlockValidator.class).validateBlocks();
                    stateLogger.reset();
                }
            }
        }
    }

    private BackupPendingSet getOptimizeSchedulePendingSet() throws IOException {
        Optional<BackupPendingSet> ret = repository.getPendingSets().stream().filter(t -> t.getSetId().equals(""))
                .findAny();
        return ret.orElse(null);
    }

    private BackupPendingSet getTrimSchedulePendingSet() throws IOException {
        Optional<BackupPendingSet> ret = repository.getPendingSets().stream().filter(t -> t.getSetId().equals("="))
                .findAny();
        return ret.orElse(null);
    }

    private boolean initializeScheduler() {
        boolean hasSchedules = false;
        lock.lock();

        try {
            try {
                repository.addDirectory(new BackupDirectory("",
                        Instant.now().toEpochMilli(),
                        null,
                        configuration.getSets().stream().flatMap(t -> t.getRoots().stream()
                                        .map(BackupSetRoot::getNormalizedPath))
                                .collect(Collectors.toCollection(TreeSet::new)), null));
            } catch (IOException e) {
                log.error("Failed to register root of backup sets in repository", e);
            }

            Map<String, BackupPendingSet> pendingScheduled = new HashMap<>();
            try {
                repository.getPendingSets().forEach((pending) -> pendingScheduled.put(pending.getSetId(), pending));
            } catch (IOException e) {
                log.warn("Failed to read scheduled sets");
            }

            for (int i = 0; i < configuration.getSets().size(); i++) {
                BackupSet set = configuration.getSets().get(i);
                BackupPendingSet pendingSet = pendingScheduled.remove(set.getId());

                if (pendingSet != null && !Objects.equals(pendingSet.getSchedule(), set.getSchedule())) {
                    if (!Strings.isNullOrEmpty(set.getSchedule())) {
                        Date date = getNextScheduleDate(set.getSchedule());
                        pendingSet.setScheduledAt(date);

                        try {
                            repository.addPendingSets(new BackupPendingSet(set.getId(), set.getSchedule(), date));
                        } catch (IOException e) {
                            log.error("Failed to update pending set \"{}\"", set.getId());
                        }
                    } else {
                        pendingSet = null;
                    }
                }

                if (pendingSet != null && (pendingSet.getScheduledAt() == null || pendingSet.getScheduledAt().after(new Date()))) {
                    if (pendingSet.getScheduledAt() != null) {
                        hasSchedules = true;
                        scheduleNextAt(set, i, pendingSet.getScheduledAt());
                    }
                } else {
                    deletePendingSet(set.getId());
                    pendingSets[i] = true;
                    if (!Strings.isNullOrEmpty(set.getSchedule())) {
                        hasSchedules = true;
                        scheduleNext(set, i);
                    }
                }
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
            log.error("Failed saving next scheduled time for backup set \"{}\"", id);
        }
    }

    private void scheduleNext(BackupSet set, int index) {
        Date date = getNextScheduleDate(set.getSchedule());
        if (date != null) {
            if (date.getTime() - new Date().getTime() > 0) {
                long dateTime = date.getTime();
                if (configuration.getManifest().getScheduleRandomize() != null) {
                    dateTime += random.nextLong(
                            Instant.now().toEpochMilli() -
                                    configuration.getManifest().getScheduleRandomize().toEpochMilli());
                }
                scheduleNextAt(set, index, new Date(dateTime));
            }
        } else {
            synchronized (scheduledTimes) {
                scheduledTimes.remove(set.getId());
                backupStatsLogger.updateScheduledTimes(scheduledTimes);
            }
            deletePendingSet(set.getId());
        }
    }

    private void scheduleNextAt(BackupSet set, int index, Date date) {
        log.info("Schedule set \"{}\" to run again at {}", set.getId(), formatTimestamp(date.getTime()));
        synchronized (scheduledTimes) {
            scheduledTimes.put(set.getId(), new Date(date.getTime()));
            backupStatsLogger.updateScheduledTimes(scheduledTimes);
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
                log.info("Restarting scan of {} for \"{}\"", set.getAllRoots(), set.getId());
                scheduledRestart = true;
                pendingSets[index] = true;
                scanner.shutdown();
                condition.signal();
            } else {
                log.info("Set \"{}\" not completed, so not restarting it", set.getId());
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
        try {
            condition.signal();
            executor.shutdown();
            scanner.shutdown();
            condition.signal();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void waitForCompletion() {
        lock.lock();
        try {
            if (!shutdown) {
                throw new IllegalStateException();
            }
            while (running) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    log.warn("Failed to wait", e);
                }
            }
        } finally {
            lock.unlock();
        }
    }
}
