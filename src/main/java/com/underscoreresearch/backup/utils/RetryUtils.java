package com.underscoreresearch.backup.utils;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;

@Slf4j
public class RetryUtils {
    public static final int DEFAULT_BASE = 1000;
    public static final int DEFAULT_RETRIES = -1; // Bump default retries to 9 which means about 8 minutes.
    private static int defaultRetries = -1;

    public static <T> T retry(Callable<T> callable,
                              ShouldRetry shouldRetry) throws Exception {
        return retry(DEFAULT_RETRIES, DEFAULT_BASE, callable, shouldRetry, true);
    }

    public static <T> T retry(int retries, int retryBase, Callable<T> callable,
                              ShouldRetry shouldRetry,
                              boolean waitForInternet) throws Exception {
        if (retries < 0) {
            if (defaultRetries < 0) {
                defaultRetries = 8;
                try {
                    BackupConfiguration config = InstanceFactory.getInstance(BackupConfiguration.class);
                    String retriesStr = config.getProperties().get("defaultRetries");
                    if (retriesStr != null) {
                        defaultRetries = Integer.parseInt(retriesStr);
                    }
                } catch (Exception ignored) {
                }
            }
            retries = defaultRetries;
        }

        for (int i = 0; true; i++) {
            try {
                if (waitForInternet) {
                    return IOUtils.waitForInternet(() -> callCallable(callable), false);
                } else {
                    return callCallable(callable);
                }
            } catch (InterruptedException | ProcessingStoppedException exc) {
                throw exc;
            } catch (Exception exc) {
                Exception thrownException;
                if (exc instanceof InternalInterruptedException) {
                    thrownException = (InterruptedException) exc.getCause();
                } else {
                    thrownException = exc;
                }
                if (retries == i || (shouldRetry != null && !shouldRetry.shouldRetry(thrownException))) {
                    throw thrownException;
                }
                LogOrWait logOrWait = shouldRetry != null ? shouldRetry.logAndWait(exc) : LogOrWait.LOG_AND_WAIT;
                if (logOrWait.shouldWait()) {
                    Thread.sleep((long) Math.pow(2, i) * retryBase);
                }
                if (logOrWait.shouldLog()) {
                    log.warn("Failed call retrying for the " + (i + 1) + " time ({})", thrownException.getMessage(), thrownException);
                }
            }
        }
    }

    private static <T> T callCallable(Callable<T> callable) throws Exception {
        try {
            return callable.call();
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
            throw new InternalInterruptedException(exc);
        }
    }

    public enum LogOrWait {
        LOG(1),
        WAIT(2),
        LOG_AND_WAIT(3),
        NONE(0);

        private final int value;

        LogOrWait(int value) {
            this.value = value;
        }

        public boolean shouldWait() {
            return (value & 2) == 2;
        }

        public boolean shouldLog() {
            return (value & 1) == 1;
        }
    }

    public interface ShouldRetry {
        boolean shouldRetry(Exception exc);

        default LogOrWait logAndWait(Exception exc) {
            return LogOrWait.LOG_AND_WAIT;
        }
    }

    private static class InternalInterruptedException extends RuntimeException {
        public InternalInterruptedException(InterruptedException exc) {
            super(exc);
        }
    }
}
