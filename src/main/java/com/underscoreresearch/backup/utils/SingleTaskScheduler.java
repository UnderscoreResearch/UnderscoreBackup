package com.underscoreresearch.backup.utils;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A scheduler that executes tasks using a single thread.
 * This class provides methods to schedule tasks either once or at a fixed rate,
 * with protection against task overlap by ensuring a minimum duration between executions.
 */
public class SingleTaskScheduler {
    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    /**
     * Creates a new SingleTaskScheduler with the specified name.
     * The name is used as a prefix for the thread name.
     *
     * @param name The name prefix for the scheduler thread
     */
    public SingleTaskScheduler(String name) {
        scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1,
                new ThreadFactoryBuilder().setNameFormat(name + "-%d").build());
    }

    /**
     * Schedules a task to run periodically at a fixed rate.
     * The task will not be executed more frequently than 90% of the specified period,
     * to prevent overlapping executions.
     *
     * @param command The task to execute
     * @param initialDelay The initial delay before the first execution
     * @param period The period between successive executions
     * @param unit The time unit for the initialDelay and period parameters
     */
    public void scheduleAtFixedRate(Runnable command,
                                    long initialDelay,
                                    long period,
                                    TimeUnit unit) {
        scheduledThreadPoolExecutor.scheduleAtFixedRate(
                new Task(command, Duration.ofMillis(unit.toMillis(period) * 9 / 10)),
                initialDelay, period, unit);
    }

    /**
     * Schedules a task to run once after the specified delay.
     *
     * @param command The task to execute
     * @param delay The delay before execution
     * @param unit The time unit for the delay parameter
     */
    public void schedule(Runnable command,
                         long delay,
                         TimeUnit unit) {
        scheduledThreadPoolExecutor.schedule(command, delay, unit);
    }

    /**
     * Initiates an orderly shutdown of the scheduler.
     * Previously submitted tasks will be executed, but no new tasks will be accepted.
     */
    public void shutdown() {
        scheduledThreadPoolExecutor.shutdown();
    }

    /**
     * Attempts to stop all actively executing tasks and halts the processing of waiting tasks.
     * Returns a list of tasks that were awaiting execution.
     */
    public void shutdownNow() {
        scheduledThreadPoolExecutor.shutdownNow();
    }

    /**
     * A wrapper for tasks that ensures they don't execute more frequently than a specified minimum duration.
     */
    private static class Task implements Runnable {
        private final Runnable command;
        private final Duration minimumDuration;
        private Instant startTime = Instant.ofEpochMilli(0);

        /**
         * Creates a new Task with the specified command and minimum duration between executions.
         *
         * @param command The task to execute
         * @param minimumDuration The minimum duration between executions
         */
        public Task(Runnable command, Duration minimumDuration) {
            this.command = command;
            this.minimumDuration = minimumDuration;
        }

        /**
         * Executes the task if the minimum duration since the last execution has elapsed.
         */
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
