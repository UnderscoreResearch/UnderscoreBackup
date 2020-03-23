package com.underscoreresearch.backup.utils;

import com.underscoreresearch.backup.io.IOUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.function.Function;

@Slf4j
public class RetryUtils {
    private static final int DEFAULT_BASE = 1000;
    private static final int MAX_RETRIES = 3;

    public static <T> T retry(Callable<T> callable,
                              Function<Exception, Boolean> shouldRetry) throws Exception {
        return retry(MAX_RETRIES, DEFAULT_BASE, callable, shouldRetry);
    }

    public static <T> T retry(int retries, int retryBase, Callable<T> callable,
                              Function<Exception, Boolean> shouldRetry) throws Exception {
        for (int i = 0; true; i++) {
            try {
                return callable.call();
            } catch (Exception exc) {
                if (retries == i || (shouldRetry != null && !shouldRetry.apply(exc))) {
                    throw exc;
                }
                if (IOUtils.hasInternet()) {
                    log.warn("Failed call " + (i + 1) + " retrying");
                }
                Thread.sleep((int) Math.pow(2, i) * retryBase);
            }
        }
    }
}
