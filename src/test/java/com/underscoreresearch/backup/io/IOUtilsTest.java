package com.underscoreresearch.backup.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IOUtilsTest {
    @Test
    public void testWaitForInternet() throws InterruptedException {
        assertTrue(IOUtils.hasInternet());
    }
}