package com.underscoreresearch.backup.io;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class IOUtilsTest {
    @Test
    public void testWaitForInternet() throws InterruptedException {
        assertTrue(IOUtils.hasInternet());
    }
}