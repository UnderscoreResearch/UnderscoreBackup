package com.underscoreresearch.backup.utils;

import java.util.concurrent.Callable;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.io.IOUtils;

@Slf4j
public class RetryUtils {
    public static final int DEFAULT_BASE = 1000;
    public static final int DEFAULT_RETRIES = 5;

    public static <T> T retry(Callable<T> callable,
                              Function<Exception, Boolean> shouldRetry) throws Exception {
        return retry(DEFAULT_RETRIES, DEFAULT_BASE, callable, shouldRetry, true);
    }

    public static <T> T retry(int retries, int retryBase, Callable<T> callable,
                              Function<Exception, Boolean> shouldRetry,
                              boolean waitForInternet) throws Exception {
        for (int i = 0; true; i++) {
            try {
                return callable.call();
            } catch (Exception exc) {
                if (retries == i || (shouldRetry != null && !shouldRetry.apply(exc))) {
                    throw exc;
                }
                Thread.sleep((long) Math.pow(2, i) * retryBase);
                if (waitForInternet && IOUtils.hasInternet()) {
                    log.warn("Failed call retrying for the " + (i + 1) + " time ({})", exc.getMessage(), exc);
                } else {
                    try {
                        return IOUtils.waitForInternet(callable);
                    } catch (Exception internetExc) {
                        log.warn("Failed to wait for internet", internetExc);
                    }
                }
            }
        }
    }
}
