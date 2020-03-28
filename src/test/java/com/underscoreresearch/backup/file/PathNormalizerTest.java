package com.underscoreresearch.backup.file;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.File;

import org.junit.jupiter.api.Test;

class PathNormalizerTest {
    @Test
    public void normalizeTest() {
        assertThat(PathNormalizer.normalizePath(File.separator + "test" + File.separator), is("/test/"));
    }

    @Test
    public void physicalTest() {
        assertThat(PathNormalizer.physicalPath("/test/"), is(File.separator + "test" + File.separator));
    }
}