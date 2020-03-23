package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

class BackupActivePathTest {
    private BackupActivePath path;

    @BeforeEach
    public void setup() {
        path = new BackupActivePath("",
                Sets.newHashSet(new BackupActiveFile("file1"), new BackupActiveFile("file2")));
        path.setParentPath("/root");
    }

    @Test
    public void assignPath() {
        path.setParentPath("/test/");
        assertThat(path.getFile(BackupFile.builder().path("/test/file1").build()).getPath(), Is.is("file1"));
        path.setParentPath("/test");
        assertThat(path.getFile(BackupFile.builder().path("/test/file1").build()).getPath(), Is.is("file1"));
    }

    @Test
    public void stripPath() {
        assertThat(BackupActivePath.stripPath("/test1/test/"), Is.is("test/"));
        assertThat(BackupActivePath.stripPath("/test1/test"), Is.is("test"));
        assertThat(BackupActivePath.stripPath("///"), Is.is("/"));
    }

    @Test
    public void findParent() {
        assertThat(BackupActivePath.findParent("/test1/test/"), Is.is("/test1/"));
        assertThat(BackupActivePath.findParent("/test1/test"), Is.is("/test1/"));
        assertThat(BackupActivePath.findParent("///"), Is.is("//"));
    }

    @Test
    public void addFile() {
        path.addFile(BackupFile.builder().path("/whatever/whatnot").build());
        assertThat(path.getFile("/whatever/whatnot"), Is.is(new BackupActiveFile("whatnot")));
    }

    @Test
    public void unprocessedFile() {
        assertThat(path.unprocessedFile("/root/file1"), Is.is(true));
        path.getFile("/root/file1").setStatus(BackupActiveStatus.INCOMPLETE);
        assertThat(path.unprocessedFile("/root/file1"), Is.is(true));
        path.getFile("/root/file1").setStatus(BackupActiveStatus.INCLUDED);
        assertThat(path.unprocessedFile("/root/file1"), Is.is(false));
        path.getFile("/root/file1").setStatus(BackupActiveStatus.EXCLUDED);
        assertThat(path.unprocessedFile("/root/file1"), Is.is(false));
        assertThat(path.unprocessedFile("/whatever/whatnot"), Is.is(false));
    }

    @Test
    public void completed() {
        assertThat(path.completed(), Is.is(false));
        path.getFile("/root/file1").setStatus(BackupActiveStatus.INCOMPLETE);
        assertThat(path.completed(), Is.is(false));
        path.getFile("/root/file2").setStatus(BackupActiveStatus.INCLUDED);
        assertThat(path.completed(), Is.is(false));
        path.getFile("/root/file1").setStatus(BackupActiveStatus.EXCLUDED);
        assertThat(path.completed(), Is.is(true));
    }

    @Test
    public void includedPaths() {
        assertThat(path.includedPaths(), Is.is(Sets.newHashSet()));
        path.getFile("/root/file2").setStatus(BackupActiveStatus.INCLUDED);
        assertThat(path.includedPaths(), Is.is(Sets.newHashSet("file2")));
        path.getFile("/root/file1").setStatus(BackupActiveStatus.EXCLUDED);
        assertThat(path.includedPaths(), Is.is(Sets.newHashSet("file2")));
    }

    @Test
    public void jsonSerialization() throws JsonProcessingException {
        String json = new ObjectMapper().writeValueAsString(path);
        assertThat(new ObjectMapper().readValue(json, BackupActivePath.class),
                Is.is(path));
    }
}