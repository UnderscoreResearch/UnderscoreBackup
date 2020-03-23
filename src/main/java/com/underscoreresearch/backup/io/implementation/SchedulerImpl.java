package com.underscoreresearch.backup.io.implementation;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.underscoreresearch.backup.utils.LogUtil.debug;

@Slf4j
public class SchedulerImpl {
    private final int maximumConcurrency;
    private ExecutorService executor;
    private List<Runnable> executingTasks = new ArrayList<>();
    private boolean shutdown;

    public SchedulerImpl(int maximumConcurrency) {
        this.maximumConcurrency = maximumConcurrency;
        executor = Executors.newFixedThreadPool(maximumConcurrency);
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
            } finally {
                synchronized (executingTasks) {
                    executingTasks.remove(this);
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

            debug(() -> log.debug(getClass().getSimpleName() + " shut down completed"));
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
