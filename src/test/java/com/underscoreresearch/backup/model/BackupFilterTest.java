package com.underscoreresearch.backup.model;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class BackupFilterTest {
    BackupFilter filter;

    @BeforeEach
    public void setup() {
        filter = BackupFilter.builder().paths(Lists.newArrayList("foo", "foobar")).type(BackupFilterType.INCLUDE).children(
                Lists.newArrayList(BackupFilter.builder().paths(Lists.newArrayList("bar")).type(BackupFilterType.EXCLUDE).children(
                        Lists.newArrayList(BackupFilter.builder().paths(Lists.newArrayList("back")).build())).build())).build();
    }

    @Test
    public void testStrippingPathSeparatorConstructor() {
        assertThat(new BackupFilter(Lists.newArrayList("test/"), null, null).getPaths(), is(Lists.newArrayList("test")));
    }

    @Test
    public void testStrippingPathSeparatorConstructor_false() {
        assertThat(new BackupFilter(Lists.newArrayList("test/test"), null, null).getPaths(),
                is(Lists.newArrayList("test/test")));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "foo/bars,true",
            "foo,true",
            "foos,false",
            "foo/bar,false",
            "foo/bar/back/,true",
            "foo/bar/back/what,true",
            "foobar,true"
    }, delimiter = ',')
    public void testMatches(String path, boolean included) {
        String matchedPath = filter.fileMatch(path);
        if (matchedPath != null) {
            assertThat(filter.includeMatchedFile(matchedPath, path), is(included));
        } else {
            assertThat(included, is(false));
        }
    }
}