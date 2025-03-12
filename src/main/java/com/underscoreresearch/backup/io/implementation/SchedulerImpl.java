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

import static com.underscoreresearch.backup.utils.LogUtil.debug;

@Slf4j
public class SchedulerImpl {
    private final int maximumConcurrency;
    private final ExecutorService executor;
    private final List<Runnable> executingTasks = new ArrayList<>();
    private final Stopwatch stopwatch = Stopwatch.createUnstarted();
    @Getter(AccessLevel.PROTECTED)
    private boolean shutdown;

    public SchedulerImpl(int maximumConcurrency) {
        this.maximumConcurrency = maximumConcurrency;
        executor = Executors.newFixedThreadPool(maximumConcurrency,
                new ThreadFactoryBuilder().setNameFormat(getClass().getSimpleName() + "-%d").build());
    }

    protected Duration getDuration() {
        return stopwatch.elapsed();
    }

    protected void resetDuration() {
        stopwatch.reset();
    }

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

    private class SchedulerTask implements Runnable {
        private final Runnable runnable;

        public SchedulerTask(Runnable runnable) {
            this.runnable = runnable;
        }

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
