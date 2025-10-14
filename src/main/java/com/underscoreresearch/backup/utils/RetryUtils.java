package com.underscoreresearch.backup.utils;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;

/**
 * Utility class for implementing retry logic with exponential backoff.
 * Provides methods to retry operations that may fail temporarily due to network issues
 * or other transient errors.
 */
@Slf4j
public class RetryUtils {
    public static final int DEFAULT_BASE = 1000;
    public static final int DEFAULT_RETRIES = -1; // Bump default retries to 9 which means about 8 minutes.
    private static int defaultRetries = -1;

    /**
     * Retries a callable operation with default retry settings.
     *
     * @param <T> The return type of the callable
     * @param callable The operation to retry
     * @param shouldRetry Predicate to determine if a specific exception should trigger a retry
     * @return The result of the callable operation
     * @throws Exception If the operation fails after all retries or with a non-retryable exception
     */
    public static <T> T retry(Callable<T> callable,
                              ShouldRetry shouldRetry) throws Exception {
        return retry(DEFAULT_RETRIES, DEFAULT_BASE, callable, shouldRetry, true);
    }

    /**
     * Retries a callable operation with configurable retry settings.
     *
     * @param <T> The return type of the callable
     * @param retries The maximum number of retries, or -1 to use the default from configuration
     * @param retryBase The base delay in milliseconds for exponential backoff
     * @param callable The operation to retry
     * @param shouldRetry Predicate to determine if a specific exception should trigger a retry
     * @param waitForInternet Whether to wait for internet connectivity before each attempt
     * @return The result of the callable operation
     * @throws Exception If the operation fails after all retries or with a non-retryable exception
     */
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

    /**
     * Calls a callable and handles interrupted exceptions by wrapping them.
     *
     * @param <T> The return type of the callable
     * @param callable The callable to execute
     * @return The result of the callable
     * @throws Exception If the callable throws an exception
     */
    private static <T> T callCallable(Callable<T> callable) throws Exception {
        try {
            return callable.call();
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
            throw new InternalInterruptedException(exc);
        }
    }

    /**
     * Enum defining logging and waiting behavior for retries.
     */
    public enum LogOrWait {
        LOG(1),
        WAIT(2),
        LOG_AND_WAIT(3),
        NONE(0);

        private final int value;

        /**
         * Constructs a LogOrWait enum with the specified value.
         *
         * @param value The bit flags for logging and waiting
         */
        LogOrWait(int value) {
            this.value = value;
        }

        /**
         * Checks if waiting is enabled for this option.
         *
         * @return True if waiting is enabled, false otherwise
         */
        public boolean shouldWait() {
            return (value & 2) == 2;
        }

        /**
         * Checks if logging is enabled for this option.
         *
         * @return True if logging is enabled, false otherwise
         */
        public boolean shouldLog() {
            return (value & 1) == 1;
        }
    }

    /**
     * Interface for determining retry behavior based on exceptions.
     */
    public interface ShouldRetry {
        /**
         * Determines if a retry should be attempted for the given exception.
         *
         * @param exc The exception that occurred
         * @return True if a retry should be attempted, false otherwise
         */
        boolean shouldRetry(Exception exc);

        /**
         * Determines the logging and waiting behavior for a retry.
         *
         * @param exc The exception that occurred
         * @return The logging and waiting behavior
         */
        default LogOrWait logAndWait(Exception exc) {
            return LogOrWait.LOG_AND_WAIT;
        }
    }

    /**
     * Internal exception used to wrap interrupted exceptions.
     */
    private static class InternalInterruptedException extends RuntimeException {
        /**
         * Constructs a new InternalInterruptedException wrapping the given interrupted exception.
         *
         * @param exc The interrupted exception to wrap
         */
        public InternalInterruptedException(InterruptedException exc) {
            super(exc);
        }
    }
}
