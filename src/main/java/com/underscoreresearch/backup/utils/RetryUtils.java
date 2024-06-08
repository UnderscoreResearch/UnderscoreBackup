package com.underscoreresearch.backup.utils;

import java.util.concurrent.Callable;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.io.IOUtils;

@Slf4j
public class RetryUtils {
    public static final int DEFAULT_BASE = 1000;
    public static final int DEFAULT_RETRIES = 9; // Bump default retries to 9 which means about 8 minutes.

    public static <T> T retry(Callable<T> callable,
                              Function<Exception, Boolean> shouldRetry) throws Exception {
        return retry(DEFAULT_RETRIES, DEFAULT_BASE, callable, shouldRetry, true);
    }

    public static <T> T retry(int retries, int retryBase, Callable<T> callable,
                              Function<Exception, Boolean> shouldRetry,
                              boolean waitForInternet) throws Exception {
        for (int i = 0; true; i++) {
            try {
                if (waitForInternet) {
                    return IOUtils.waitForInternet(() -> callCallable(callable), false);
                } else {
                    return callCallable(callable);
                }
            } catch (InterruptedException exc) {
                throw exc;
            } catch (Exception exc) {
                Exception thrownException;
                if (exc instanceof InternalInterruptedException) {
                    thrownException = (InterruptedException) exc.getCause();
                } else {
                    thrownException = exc;
                }
                if (retries == i || (shouldRetry != null && !shouldRetry.apply(thrownException))) {
                    throw thrownException;
                }
                Thread.sleep((long) Math.pow(2, i) * retryBase);
                log.warn("Failed call retrying for the " + (i + 1) + " time ({})", thrownException.getMessage(), thrownException);
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

    private static class InternalInterruptedException extends RuntimeException {
        public InternalInterruptedException(InterruptedException exc) {
            super(exc);
        }
    }
}
