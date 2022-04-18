package com.underscoreresearch.backup.io.implementation;

import static com.underscoreresearch.backup.utils.LogUtil.debug;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.extern.slf4j.Slf4j;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

@Slf4j
public class SchedulerImpl {
    private final int maximumConcurrency;
    private ExecutorService executor;
    private List<Runnable> executingTasks = new ArrayList<>();
    private boolean shutdown;
    private Stopwatch stopwatch = Stopwatch.createUnstarted();

    protected Duration getDuration() {
        return stopwatch.elapsed();
    }

    protected void resetDuration() {
        stopwatch.reset();
    }

    public SchedulerImpl(int maximumConcurrency) {
        this.maximumConcurrency = maximumConcurrency;
        executor = Executors.newFixedThreadPool(maximumConcurrency,
                new ThreadFactoryBuilder().setNameFormat(getClass().getSimpleName() + "-%d").build());
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
                    if (executingTasks.size() == 0) {
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

    protected void schedule(Runnable runnable) {
        synchronized (executingTasks) {
            if (shutdown) {
                return;
            }
            runnable = new SchedulerTask(runnable);
            executingTasks.add(runnable);
            while (executingTasks.indexOf(runnable) >= maximumConcurrency) {
                try {
                    if (shutdown) {
                        executingTasks.remove(runnable);
                        executingTasks.notifyAll();
                        return;
                    }
                    executingTasks.wait();
                } catch (InterruptedException e) {
                    log.warn("Failed to wait", e);
                }
            }
        }

        synchronized (stopwatch) {
            if (!stopwatch.isRunning())
                stopwatch.start();
        }
        executor.submit(runnable);
    }

    public void shutdown() {
        synchronized (executingTasks) {
            shutdown = true;
            executingTasks.notifyAll();
            debug(() -> log.debug(getClass().getSimpleName() + " shutting down"));

            while (executingTasks.size() > 0) {
                try {
                    executingTasks.wait();
                } catch (InterruptedException e) {
                    log.warn("Failed to wait", e);
                }
            }

            debug(() -> log.debug(getClass().getSimpleName() + " shutdown completed"));
            executor.shutdown();
        }
    }

    public void waitForCompletion() {
        synchronized (executingTasks) {
            while (executingTasks.size() > 0) {
                try {
                    executingTasks.wait();
                } catch (InterruptedException e) {
                    log.warn("Failed to wait", e);
                }
            }
        }
    }
}
