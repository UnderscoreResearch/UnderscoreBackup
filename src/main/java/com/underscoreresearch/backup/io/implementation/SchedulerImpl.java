package com.underscoreresearch.backup.io.implementation;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.underscoreresearch.backup.utils.log.LogUtil.debug;

/**
 * Base implementation for schedulers that manage concurrent tasks.
 * Provides common functionality for task scheduling and execution.
 */
@Slf4j
public class SchedulerImpl {
    private final int maximumConcurrency;
    private final ExecutorService executor;
    private final List<Runnable> executingTasks = new ArrayList<>();
    private final Stopwatch stopwatch = Stopwatch.createUnstarted();
    @Getter(AccessLevel.PROTECTED)
    private boolean shutdown;

    /**
     * Constructor for SchedulerImpl.
     *
     * @param maximumConcurrency The maximum number of concurrent tasks
     */
    public SchedulerImpl(int maximumConcurrency) {
        this.maximumConcurrency = maximumConcurrency;
        executor = Executors.newFixedThreadPool(maximumConcurrency,
                new ThreadFactoryBuilder().setNameFormat(getClass().getSimpleName() + "-%d").build());
    }

    /**
     * Get the elapsed duration since the scheduler started.
     *
     * @return The elapsed duration
     */
    protected Duration getDuration() {
        return stopwatch.elapsed();
    }

    /**
     * Reset the duration timer.
     */
    protected void resetDuration() {
        stopwatch.reset();
    }

    /**
     * Schedule a task for execution.
     * The task will be executed when a thread becomes available.
     *
     * @param runnable The task to execute
     * @return True if the task was scheduled, false if the scheduler is shutting down
     */
    protected boolean schedule(Runnable runnable) {
        synchronized (executingTasks) {
            if (shutdown) {
                return false;
            }
            runnable = new SchedulerTask(runnable);
            executingTasks.add(runnable);
            while (executingTasks.indexOf(runnable) >= maximumConcurrency) {
                try {
                    if (shutdown) {
                        executingTasks.remove(runnable);
                        executingTasks.notifyAll();
                        return false;
                    }
                    executingTasks.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Failed to wait", e);
                }
            }
        }

        synchronized (stopwatch) {
            if (!stopwatch.isRunning())
                stopwatch.start();
        }
        executor.submit(runnable);
        return true;
    }

    /**
     * Shutdown the scheduler and wait for all tasks to complete.
     * No new tasks will be accepted after this method is called.
     */
    public void shutdown() {
        synchronized (executingTasks) {
            shutdown = true;
            executingTasks.notifyAll();
            debug(() -> log.debug(getClass().getSimpleName() + " shutting down"));

            while (!executingTasks.isEmpty()) {
                try {
                    executingTasks.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Failed to wait", e);
                }
            }

            debug(() -> log.debug(getClass().getSimpleName() + " shutdown completed"));
            executor.shutdown();
        }
    }

    /**
     * Wait for all scheduled tasks to complete.
     * This method blocks until all tasks have finished.
     */
    public void waitForCompletion() {
        synchronized (executingTasks) {
            while (!executingTasks.isEmpty()) {
                try {
                    executingTasks.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Failed to wait", e);
                }
            }
        }
    }

    /**
     * Wrapper for tasks that handles cleanup after execution.
     */
    private class SchedulerTask implements Runnable {
        private final Runnable runnable;

        /**
         * Constructor for SchedulerTask.
         *
         * @param runnable The task to wrap
         */
        public SchedulerTask(Runnable runnable) {
            this.runnable = runnable;
        }

        /**
         * Execute the task and handle cleanup.
         */
        @Override
        public void run() {
            try {
                runnable.run();
            } catch (Throwable exc) {
                log.error("Encountered error executing task", exc);
            } finally {
                synchronized (executingTasks) {
                    executingTasks.remove(this);
                    if (executingTasks.isEmpty()) {
                        synchronized (stopwatch) {
                            if (stopwatch.isRunning())
                                stopwatch.stop();
                        }
                    }
                    executingTasks.notifyAll();
                }
            }
        }
    }
}
