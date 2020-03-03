package com.underscoreresearch.backup.file.implementation;

import static com.underscoreresearch.backup.utils.LogUtil.formatTimestamp;

import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.apache.logging.log4j.core.util.CronExpression;

import com.google.common.base.Strings;
import com.underscoreresearch.backup.file.FileScanner;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.ScannerScheduler;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupSet;

@Slf4j
public class ScannerSchedulerImpl implements ScannerScheduler {
    private final BackupConfiguration configuration;
    private final FileScanner scanner;
    private final MetadataRepository repository;
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    private final boolean[] pendingSets;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private boolean shutdown;
    private boolean scheduledRestart;

    public ScannerSchedulerImpl(BackupConfiguration configuration,
                                MetadataRepository repository,
                                FileScanner scanner) {
        this.configuration = configuration;
        this.scanner = scanner;
        this.repository = repository;

        pendingSets = new boolean[configuration.getSets().size()];
    }

    @Override
    public void start() {
        lock.lock();
        boolean hasSchedules = false;

        try {
            try {
                repository.addDirectory("",
                        Instant.now().toEpochMilli(),
                        configuration.getSets().stream().map(t -> t.getNormalizedRoot())
                                .collect(Collectors.toSet()));
            } catch (IOException e) {
                log.error("Failed to register root of backup sets in repository", e);
            }

            for (int i = 0; i < configuration.getSets().size(); i++) {
                pendingSets[i] = true;
                BackupSet set = configuration.getSets().get(i);

                if (!Strings.isNullOrEmpty(set.getSchedule())) {
                    hasSchedules = true;
                    scheduleNext(set, i);
                }
            }
        } finally {
            lock.unlock();
        }

        lock.lock();
        while (!shutdown) {
            int i = 0;
            while (i < pendingSets.length && !shutdown) {
                if (pendingSets[i]) {
                    lock.unlock();
                    BackupSet set = configuration.getSets().get(i);
                    try {
                        log.info("Started scanning {} for {}", set.getRoot(), set.getId());
                        if (scanner.startScanning(set)) {
                            pendingSets[i] = false;
                            i++;
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
                }
            }

            if (!shutdown) {
                if (!hasSchedules) {
                    break;
                }
                try {
                    log.info("Pausing for next scheduled scan");
                    try {
                        repository.close();
                    } catch (IOException e) {
                        log.error("Failed to close repository before waiting", e);
                    }
                    System.gc();
                    condition.await();
                } catch (InterruptedException e) {
                    log.error("Failed to wait", e);
                }
            }
        }
        lock.unlock();
    }

    private void scheduleNext(BackupSet set, int index) {
        try {
            CronExpression expression = new CronExpression(set.getSchedule());
            Date now = new Date();
            Date date = expression.getNextValidTimeAfter(now);

            if (date != null && date.getTime() - now.getTime() > 0) {
                log.info("Schedule set {} to run again at {}", set.getId(), formatTimestamp(date.getTime()));
                executor.schedule(() -> {
                            lock.lock();
                            try {
                                if (!pendingSets[index]) {
                                    log.info("Restarting scan of {} for {}", set.getRoot(), set.getId());
                                    scheduledRestart = true;
                                    pendingSets[index] = true;
                                    scanner.shutdown();
                                    condition.signal();
                                } else {
                                    log.info("Set {} not completed, so not restarting it", set.getRoot(), set.getId());
                                }
                                scheduleNext(set, index);
                            } finally {
                                lock.unlock();
                            }
                        },
                        date.getTime() - now.getTime(),
                        TimeUnit.MILLISECONDS);
            } else {
                log.warn("No new time to schedule set");
            }
        } catch (ParseException e) {
            log.error("Invalid cron expression, will not reschedule after initial run: {}",
                    set.getSchedule(), e);
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
}