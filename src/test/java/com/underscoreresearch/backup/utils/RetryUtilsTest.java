package com.underscoreresearch.backup.utils;

import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.cli.ParseException;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RetryUtilsTest {
    @Test
    public void retryTestSecondSucceed() throws Exception {
        AtomicInteger integer = new AtomicInteger();
        assertThat(RetryUtils.retry(() -> {
            if (integer.incrementAndGet() < 2) {
                throw new Exception("");
            }
            return integer.get();
        }, null), Is.is(2));

        assertThat(integer.get(), Is.is(2));
    }

    @Test
    public void firstTrySucceed() throws Exception {
        AtomicInteger integer = new AtomicInteger();
        assertThat(RetryUtils.retry(() -> integer.incrementAndGet(), null), Is.is(1));
    }

    @Test
    public void noRetry() {
        AtomicInteger integer = new AtomicInteger();
        Assertions.assertThrows(ParseException.class, () -> {
            RetryUtils.retry(() -> {
                integer.incrementAndGet();
                throw new ParseException("");
            }, (exc) -> !(exc instanceof ParseException));
        });
        assertThat(integer.get(), Is.is(1));
    }

    @Test
    public void failAfterRetry() {
        AtomicInteger integer = new AtomicInteger();
        Assertions.assertThrows(ParseException.class, () -> {
            RetryUtils.retry(() -> {
                integer.incrementAndGet();
                throw new ParseException("");
            }, (exc) -> (exc instanceof ParseException));
        });
        assertThat(integer.get(), Is.is(6));
    }

    @Test
    public void testExceptionType() throws Exception {
        Assertions.assertThrows(IOException.class, () -> RetryUtils.retry(() -> {
            throw new IOException("Message");
        }, (exc) -> {
            assertThat(exc.getClass(), Is.is(IOException.class));
            assertThat(exc.getMessage(), Is.is("Message"));
            return false;
        }));
    }
}