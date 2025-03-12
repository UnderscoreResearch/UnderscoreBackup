package com.underscoreresearch.backup.utils;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SingleTaskScheduler {
    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    public SingleTaskScheduler(String name) {
        scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1,
                new ThreadFactoryBuilder().setNameFormat(name + "-%d").build());
    }

    public void scheduleAtFixedRate(Runnable command,
                                    long initialDelay,
                                    long period,
                                    TimeUnit unit) {
        scheduledThreadPoolExecutor.scheduleAtFixedRate(
                new Task(command, Duration.ofMillis(unit.toMillis(period) * 9 / 10)),
                initialDelay, period, unit);
    }

    public void schedule(Runnable command,
                         long delay,
                         TimeUnit unit) {
        scheduledThreadPoolExecutor.schedule(command, delay, unit);
    }

    public void shutdown() {
        scheduledThreadPoolExecutor.shutdown();
    }

    public void shutdownNow() {
        scheduledThreadPoolExecutor.shutdownNow();
    }

    private static class Task implements Runnable {
        private final Runnable command;
        private final Duration minimumDuration;
        private Instant startTime = Instant.ofEpochMilli(0);

        public Task(Runnable command, Duration minimumDuration) {
            this.command = command;
            this.minimumDuration = minimumDuration;
        }

        @Override
        public void run() {
            Instant now = Instant.now();
            if (startTime.plus(minimumDuration).isAfter(now)) {
                return;
            }
            startTime = now;
            command.run();
        }
    }
}
