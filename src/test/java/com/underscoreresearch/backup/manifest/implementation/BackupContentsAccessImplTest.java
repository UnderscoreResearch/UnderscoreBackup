package com.underscoreresearch.backup.manifest.implementation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.nullValue;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupFile;

class BackupContentsAccessImplTest {
    private MetadataRepository metadataRepository;
    private BackupContentsAccess backupContentsAccess;
    private BackupContentsAccess backupContentsAccessCurrent;
    private BackupContentsAccess backupContentsAccessEarly;
    private BackupContentsAccessImpl backupContentsAccessTooEarly;

    @BeforeEach
    public void test() throws IOException {
        metadataRepository = Mockito.mock(MetadataRepository.class);

        Mockito.when(metadataRepository.lastDirectory(""))
                .thenReturn(new BackupDirectory("", 3L,
                        Sets.newTreeSet(Lists.newArrayList("/test/set1/", "/test/set2/", "/"))));
        Mockito.when(metadataRepository.directory("")).thenReturn(Lists.newArrayList(
                new BackupDirectory("", 2L, Sets.newTreeSet(Lists.newArrayList("/test/set1/", "/"))),
                new BackupDirectory("", 3L, Sets.newTreeSet(Lists.newArrayList("/test/set1/", "/test/set2/", "/")))
        ));


        Mockito.when(metadataRepository.lastDirectory("/"))
                .thenReturn(new BackupDirectory("/", 3L, Sets.newTreeSet(Lists.newArrayList("dir2/"))));
        Mockito.when(metadataRepository.directory("/")).thenReturn(
                Lists.newArrayList(
                        new BackupDirectory("/", 2L, Sets.newTreeSet(Lists.newArrayList("dir1/", "dir2/"))),
                        new BackupDirectory("/", 3L, Sets.newTreeSet(Lists.newArrayList("dir2/")))));

        Mockito.when(metadataRepository.directory("/test/set1/")).thenReturn(Lists.newArrayList(
                new BackupDirectory("/test/set/", 2L, Sets.newTreeSet(Lists.newArrayList("file1", "dir/"))),
                new BackupDirectory("/test/set/", 3L, Sets.newTreeSet(Lists.newArrayList("file1", "file2", "dir/")))
        ));
        Mockito.when(metadataRepository.lastDirectory("/test/set1/"))
                .thenReturn(new BackupDirectory("/test/set/", 3L,
                        Sets.newTreeSet(Lists.newArrayList("file1", "file2", "dir/"))));

        Mockito.when(metadataRepository.file("/test/set1/file1"))
                .thenReturn(Lists.newArrayList(BackupFile.builder().path("/test/set1/file1").lastChanged(2L).length(2L).build(),
                        BackupFile.builder().path("/test/set1/file1").lastChanged(4L).length(4L).build()));
        Mockito.when(metadataRepository.file("/test/set1/file2"))
                .thenReturn(Lists.newArrayList(
                        BackupFile.builder().path("/test/set1/file2").lastChanged(4L).length(4L).build()));

        Mockito.when(metadataRepository.lastFile("/test/set1/file1"))
                .thenReturn(BackupFile.builder().path("/test/set1/file1").lastChanged(4L).length(4L).build());
        Mockito.when(metadataRepository.lastFile("/test/set1/file2"))
                .thenReturn(BackupFile.builder().path("/test/set1/file2").lastChanged(4L).length(4L).build());

        backupContentsAccess = new BackupContentsAccessImpl(metadataRepository, null);
        backupContentsAccessCurrent = new BackupContentsAccessImpl(metadataRepository, Instant.now().toEpochMilli());
        backupContentsAccessEarly = new BackupContentsAccessImpl(metadataRepository, 2L);
        backupContentsAccessTooEarly = new BackupContentsAccessImpl(metadataRepository, 1L);
    }

    @Test
    public void testEarly() throws IOException {
        assertThat(backupContentsAccessEarly.directoryFiles("/"), Is.is(newFileSet(file("/dir1/"), file("/dir2/"), file("/test/"))));
        assertThat(backupContentsAccessEarly.directoryFiles("/test/"), Is.is(newFileSet(file("/test/set1/", 2L))));
        assertThat(backupContentsAccessEarly.directoryFiles("/test/set1/"), Is.is(newFileSet(file("/test/set1/dir/"), file("/test/set1/file1", 2L))));
        assertThat(backupContentsAccessEarly.directoryFiles("/whatever"), nullValue());
    }

    @Test
    public void testTooEarly() throws IOException {
        assertThat(backupContentsAccessTooEarly.directoryFiles("/"), nullValue());
        assertThat(backupContentsAccessTooEarly.directoryFiles("/test/"), nullValue());
        assertThat(backupContentsAccessTooEarly.directoryFiles("/test/set1/"), nullValue());
        assertThat(backupContentsAccessTooEarly.directoryFiles("/whatever"), nullValue());
    }

    @Test
    public void testCurrent() throws IOException {
        assertThat(backupContentsAccessCurrent.directoryFiles("/"), Is.is(newFileSet(file("/dir2/"), file("/test/"))));
        assertThat(backupContentsAccessCurrent.directoryFiles("/test/"), Is.is(newFileSet(file("/test/set1/", 3L), file("/test/set2/"))));
        assertThat(backupContentsAccessCurrent.directoryFiles("/test/set1/"), Is.is(newFileSet(file("/test/set1/dir/"), file("/test/set1/file1", 4L), file("/test/set1/file2", 4L))));
        assertThat(backupContentsAccessCurrent.directoryFiles("/whatever"), nullValue());

        assertThat(backupContentsAccess.directoryFiles(""), Is.is(newFileSet(file("/dir2/"), file("/test/"))));
        assertThat(backupContentsAccess.directoryFiles("/test"), Is.is(newFileSet(file("/test/set1/", 3L), file("/test/set2/"))));
        assertThat(backupContentsAccess.directoryFiles("/test/set1"), Is.is(newFileSet(file("/test/set1/dir/"), file("/test/set1/file1", 4L), file("/test/set1/file2", 4L))));
        assertThat(backupContentsAccess.directoryFiles("/whatever"), nullValue());

    }

    private List<BackupFile> newFileSet(BackupFile... files) {
        ArrayList<BackupFile> ret = new ArrayList<>();
        for (BackupFile file : files) {
            ret.add(file);
        }
        return ret;
    }

    private BackupFile file(String path) {
        return BackupFile.builder().path(path).build();
    }

    private BackupFile file(String path, Long duration) {
        if (path.endsWith("/"))
            return BackupFile.builder().path(path).lastChanged(duration).build();
        return BackupFile.builder().path(path).lastChanged(duration).length(duration).build();
    }
}