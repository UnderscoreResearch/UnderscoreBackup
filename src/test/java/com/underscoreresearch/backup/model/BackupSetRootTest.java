package com.underscoreresearch.backup.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

class BackupSetRootTest {
    private BackupSetRoot root;

    @BeforeEach
    public void setup() {
        root = BackupSetRoot.builder().path("/").filters(Lists.newArrayList(BackupFilter.builder()
                .type(BackupFilterType.EXCLUDE)
                .paths(Lists.newArrayList("C:"))
                .children(Lists.newArrayList(BackupFilter.builder().paths(Lists.newArrayList("A/B"))
                        .type(BackupFilterType.INCLUDE).build()))
                .build())).build();
    }

    @Test
    public void testIncludeDirectory() {
        assertTrue(root.includeDirectory("C:/"));
        assertFalse(root.includeDirectory("C:/B/"));
        assertTrue(root.includeDirectory("C:/A/"));
        assertTrue(root.includeDirectory("C:/A/B/"));
        assertTrue(root.includeDirectory("C:/A/B/C"));
    }

    @Test
    public void testIncludeFile() {
        assertFalse(root.includeFile("C:", null));
        assertFalse(root.includeFile("C:/A", null));
        assertTrue(root.includeFile("C:/A/B", null));
        assertTrue(root.includeFile("C:/A/B/C", null));
        assertFalse(root.includeFile("C:/B/", null));
    }
}