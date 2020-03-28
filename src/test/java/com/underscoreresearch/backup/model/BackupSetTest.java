package com.underscoreresearch.backup.model;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.google.common.collect.Lists;

class BackupSetTest {
    BackupFilter filter1;
    BackupFilter filter2;
    BackupSet set;
    BackupSet rootSet;

    @BeforeEach
    public void setup() {
        filter2 = BackupFilter.builder().paths(Lists.newArrayList("foobar/test",
                "home/ANT.AMAZON.COM/mauritz/.gradle"))
                .type(BackupFilterType.EXCLUDE).build();
        filter1 = BackupFilter.builder().paths(Lists.newArrayList("foo")).type(BackupFilterType.INCLUDE).children(
                Lists.newArrayList(BackupFilter.builder().paths(Lists.newArrayList("bar")).type(BackupFilterType.EXCLUDE).children(
                        Lists.newArrayList(BackupFilter.builder().paths(Lists.newArrayList("back")).build())).build())).build();

        set = BackupSet.builder()
                .root("/root/")
                .filters(Lists.newArrayList(filter1, filter2))
                .exclusions(Lists.newArrayList("\\.bak$")).build();

        rootSet = BackupSet.builder()
                .filters(Lists.newArrayList(filter1, filter2)).build();
        rootSet.setNormalizedRoot("/");
    }

    @Test
    public void otherSet() {
        set = BackupSet.builder()
                .filters(Lists.newArrayList(filter1, filter2)).build();
        set.setNormalizedRoot("/root");
        assertThat(set.includeFile("/root/foo.bak"), is(true));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/roots,false",
            "/root/foo.back,true",
            "/root/foo.bak,false",
            "/root,true",
            "/roots,false",
            "/root/,true",
            "/root/foo/bars,true",
            "/root/foo,true",
            "/root/foos,true",
            "/root/foo/bar,false",
            "/root/foo/bar/back/,true",
            "/root/foo/bar/back/what,true",
            "/root/foobar/test,false",
            "/root/foobar/test/sub,false"
    }, delimiter = ',')
    public void testMatches(String path, boolean included) {
        assertThat(path, set.includeFile(path), is(included));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/root,true",
            "/root/foo,true",
            "/root/,true",
            "/roots,false",
            "/root/foobar/test/sub,true"
    }, delimiter = ',')
    public void testInRoot(String path, boolean included) {
        assertThat(path, set.inRoot(path), is(included));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/root/foobar/test,false",
            "/root/foobar/test/sub,false",
            "/roots,false",
            "/root/foo.back,true",
            "/root/foobar,true",
            "/root/foobar/false,true",
            "/root/foo.bak,true",
            "/root,true",
            "/roots,false",
            "/root/,true",
            "/root/foo/bars,true",
            "/root/foo,true",
            "/root/foos,true",
            "/root/foo/bar,true",
            "/root/foo/bar/back/,true",
            "/root/foo/bar/back/what,true",
    }, delimiter = ',')
    public void testInDirectory(String path, boolean included) {
        assertThat(path, set.includeDirectory(path), is(included));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/home/ANT.AMAZON.COM/mauritz/.gradle,false",
            "/root/foobar/test/sub,true",
    }, delimiter = ',')
    public void testRootInDirectory(String path, boolean included) {
        assertThat(path, rootSet.includeDirectory(path), is(included));
    }
}