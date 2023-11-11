package com.underscoreresearch.backup.file.implementation;

import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

import org.hamcrest.Matchers;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

@Slf4j
class BackupStatsLoggerTest {
    private static final String TEST_MESSAGE = "Test \"message\"\nWith redacted numbers \u200Enum\u200E";
    private static final String TEST_ANSWER = "Test {REDACTED}\nWith redacted numbers {REDACTED}";

    @Test
    public void testErrorReporting() throws IOException {
        File file = File.createTempFile("underscorebackup", "tmp");
        file.delete();
        file.mkdirs();
        new BackupStatsLogger(null, file.toString());
        log.info(TEST_MESSAGE);
        BackupStatsLogger.writeEncounteredError(TEST_MESSAGE.getBytes(StandardCharsets.UTF_8));
        assertThat(BackupStatsLogger.extractEncounteredError(), Is.is(TEST_ANSWER));
        assertThat(BackupStatsLogger.extractEncounteredError(), Matchers.nullValue());
    }
}