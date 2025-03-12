package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

class BackupFilePartTest {
    @Test
    public void legacyDeserialization() throws JsonProcessingException {
        String oldFormat = "{\"blockHash\": \"bh\", \"partHash\": \"ph\", \"blockIndex\": 1}";

        assertThat(new ObjectMapper().readValue(oldFormat, BackupFilePart.class),
                Is.is(new BackupFilePart("bh", "ph", 1, null)));
    }
}