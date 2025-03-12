package com.underscoreresearch.backup.model;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.file.PathNormalizer;
import org.apache.commons.lang.SystemUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class BackupSetTest {
    BackupFilter filter1;
    BackupFilter filter2;
    BackupSet set;
    BackupSet rootSet;
    private String systemTypePrefix;

    @BeforeEach
    public void setup() {
        if (SystemUtils.IS_OS_WINDOWS) {
            systemTypePrefix = "C:";

            filter2 = BackupFilter.builder().paths(Lists.newArrayList("foobar/test",
                            systemTypePrefix + "/home/mauritz/.gradle"))
                    .type(BackupFilterType.EXCLUDE).build();
        } else {
            systemTypePrefix = "";

            filter2 = BackupFilter.builder().paths(Lists.newArrayList("foobar/test",
                            systemTypePrefix + "home/mauritz/.gradle"))
                    .type(BackupFilterType.EXCLUDE).build();
        }
        filter1 = BackupFilter.builder().paths(Lists.newArrayList("foo")).type(BackupFilterType.INCLUDE).children(
                Lists.newArrayList(BackupFilter.builder().paths(Lists.newArrayList("bar")).type(BackupFilterType.EXCLUDE).children(
                        Lists.newArrayList(BackupFilter.builder().paths(Lists.newArrayList("back")).build())).build())).build();

        set = BackupSet.builder()
                .roots(Lists.newArrayList(BackupSetRoot.builder()
                        .filters(Lists.newArrayList(filter1, filter2))
                        .path(systemTypePrefix + "/another/root/").build()))
                .exclusions(Lists.newArrayList("\\.bak$")).build();

        rootSet = BackupSet.builder()
                .roots(Lists.newArrayList(BackupSetRoot.builder()
                        .filters(Lists.newArrayList(filter1, filter2)).build()))
                .build();
        rootSet.getRoots().get(0).setNormalizedPath("/");
    }

    @Test
    public void otherSet() {
        BackupSetRoot root = BackupSetRoot.builder()
                .filters(Lists.newArrayList(filter1, filter2)).build();
        root.setNormalizedPath("/root");
        set = BackupSet.builder()
                .roots(Lists.newArrayList(root))
                .build();
        assertThat(set.includeFile("/root/foo.bak"), is(true));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/another/root/foo/bar,false",
            "/another/roots,false",
            "/another/root/foo.back,true",
            "/another/root/foo.bak,false",
            "/another/root,true",
            "/another/roots,false",
            "/another/root/,true",
            "/another/root/foo/bars,true",
            "/another/root/foo,true",
            "/another/root/foos,true",
            "/another/root/foo/bar/back/,true",
            "/another/root/foo/bar/back/what,true",
            "/another/root/foobar/test,false",
            "/another/root/foobar/test/sub,false"
    }, delimiter = ',')
    public void testMatches(String path, boolean included) {
        assertThat(path, set.includeFile(systemTypePrefix + path), is(included));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/another/root,true",
            "/another/root/foo,true",
            "/another/root/,true",
            "/another/roots,false",
            "/another/root/foobar/test/sub,true"
    }, delimiter = ',')
    public void testInRoot(String path, boolean included) {
        assertThat(path, set.inRoot(systemTypePrefix + path), is(included));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/another/root/foobar,true",
            "/another/root/foobar/test,false",
            "/another/root/foobar/test/sub,false",
            "/another/roots,false",
            "/another/root/foo.back,true",
            "/another/root/foobar/false,true",
            "/another/root/foo.bak,true",
            "/another/root,true",
            "/another/roots,false",
            "/another/root/,true",
            "/another/root/foo/bars,true",
            "/another/root/foo,true",
            "/another/root/foos,true",
            "/another/root/foo/bar/,true",
            "/another/root/foo/bar/back/,true",
            "/another/root/foo/bar/back/what,true",
            "/another/root/foo/bar/foo/,false"
    }, delimiter = ',')
    public void testInDirectory(String path, boolean included) {
        assertThat(path, set.includeDirectory(systemTypePrefix + path), is(included));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/,true",
            "/another/,true",
            "/another/root/,true",
            "/another/root/foobar/test,false",
            "/another/root/foobar/test/sub,false",
            "/another/roots,false",
            "/another/root/foo.back,true",
            "/another/root/foobar,true",
            "/another/root/foobar/false,true",
            "/another/root/foo.bak,false",
            "/another/root,true",
            "/another/roots,false",
            "/another/root/,true",
            "/another/root/foo/bars,true",
            "/another/root/foo,true",
            "/another/root/foos,true",
            "/another/root/foo/bar,false",
            "/another/root/foo/bar/,true",
            "/another/root/foo/bar/foo/,false",
            "/another/root/foo/bar/back/,true",
            "/another/root/foo/bar/back/what,true"
    }, delimiter = ',')
    public void testInShare(String path, boolean included) {
        assertThat(path, set.includeForShare(!path.equals(PathNormalizer.ROOT) ? systemTypePrefix + path : path), is(included));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/home/mauritz/.gradle,false",
            "/root/foobar/test/sub,true",
    }, delimiter = ',')
    public void testRootInDirectory(String path, boolean included) {
        assertThat(path, rootSet.includeDirectory(systemTypePrefix + path), is(included));
    }
}