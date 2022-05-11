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
    private BackupContentsAccess backupContentsAccessTooEarly;
    private BackupContentsAccess backupContentsAccessTooEarlyIncludeDeleted;
    private BackupContentsAccess backupContentsAccessEarlyIncludeDeleted;
    private BackupContentsAccess backupContentsAccessNowIncludeDeleted;

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
        Mockito.when(metadataRepository.directory("/dir1/")).thenReturn(
                Lists.newArrayList(
                        new BackupDirectory("/dir1/", 2L, Sets.newTreeSet(Lists.newArrayList("fileDeleted")))));

        Mockito.when(metadataRepository.directory("/test/set1/")).thenReturn(Lists.newArrayList(
                new BackupDirectory("/test/set/", 2L, Sets.newTreeSet(Lists.newArrayList("file1", "dir/"))),
                new BackupDirectory("/test/set/", 3L, Sets.newTreeSet(Lists.newArrayList("file1", "file2", "dir/")))
        ));
        Mockito.when(metadataRepository.lastDirectory("/test/set1/"))
                .thenReturn(new BackupDirectory("/test/set/", 3L,
                        Sets.newTreeSet(Lists.newArrayList("file1", "file2", "dir/"))));

        Mockito.when(metadataRepository.file("/test/set1/file1"))
                .thenReturn(Lists.newArrayList(BackupFile.builder().path("/test/set1/file1").added(2L).lastChanged(2L).length(2L).build(),
                        BackupFile.builder().path("/test/set1/file1").added(4L).lastChanged(4L).length(4L).build()));
        Mockito.when(metadataRepository.file("/dir1/fileDeleted"))
                .thenReturn(Lists.newArrayList(BackupFile.builder().path("/dir1/fileDeleted").added(2L).lastChanged(2L).length(2L).build()));
        Mockito.when(metadataRepository.file("/test/set1/file2"))
                .thenReturn(Lists.newArrayList(
                        BackupFile.builder().path("/test/set1/file2").added(4L).lastChanged(4L).length(4L).build()));

        Mockito.when(metadataRepository.lastFile("/test/set1/file1"))
                .thenReturn(BackupFile.builder().path("/test/set1/file1").added(4L).lastChanged(4L).length(4L).build());
        Mockito.when(metadataRepository.lastFile("/dir1/fileDeleted"))
                .thenReturn(BackupFile.builder().path("/dir1/fileDeleted").added(2L).lastChanged(2L).length(2L).build());
        Mockito.when(metadataRepository.lastFile("/test/set1/file2"))
                .thenReturn(BackupFile.builder().path("/test/set1/file2").added(4L).lastChanged(4L).length(4L).build());

        backupContentsAccess = new BackupContentsAccessImpl(metadataRepository, null, false);
        backupContentsAccessCurrent = new BackupContentsAccessImpl(metadataRepository, Instant.now().toEpochMilli(), false);
        backupContentsAccessEarly = new BackupContentsAccessImpl(metadataRepository, 2L, false);
        backupContentsAccessTooEarly = new BackupContentsAccessImpl(metadataRepository, 1L, false);

        backupContentsAccessTooEarlyIncludeDeleted = new BackupContentsAccessImpl(metadataRepository, 1L, true);
        backupContentsAccessEarlyIncludeDeleted = new BackupContentsAccessImpl(metadataRepository, 2L, true);
        backupContentsAccessNowIncludeDeleted = new BackupContentsAccessImpl(metadataRepository, null, true);
    }

    @Test
    public void testEarly() throws IOException {
        assertThat(backupContentsAccessEarly.directoryFiles("/"), Is.is(newFileSet(file("/dir1/", 2L), file("/dir2/"), file("/test/"))));
        assertThat(backupContentsAccessEarly.directoryFiles("/test/"), Is.is(newFileSet(file("/test/set1/", 2L))));
        assertThat(backupContentsAccessEarly.directoryFiles("/test/set1/"), Is.is(newFileSet(file("/test/set1/dir/"), file("/test/set1/file1", 2L))));
        assertThat(backupContentsAccessEarly.directoryFiles("/whatever"), nullValue());

        assertThat(backupContentsAccessEarlyIncludeDeleted.directoryFiles("/"), Is.is(newFileSet(file("/dir1/", 2L), file("/dir2/"), file("/test/"))));
        assertThat(backupContentsAccessEarlyIncludeDeleted.directoryFiles("/test/"), Is.is(newFileSet(file("/test/set1/", 2L))));
        assertThat(backupContentsAccessEarlyIncludeDeleted.directoryFiles("/test/set1/"), Is.is(newFileSet(file("/test/set1/dir/"), file("/test/set1/file1", 2L))));
        assertThat(backupContentsAccessEarlyIncludeDeleted.directoryFiles("/whatever"), nullValue());
    }

    @Test
    public void testTooEarly() throws IOException {
        assertThat(backupContentsAccessTooEarly.directoryFiles("/"), nullValue());
        assertThat(backupContentsAccessTooEarly.directoryFiles("/test/"), nullValue());
        assertThat(backupContentsAccessTooEarly.directoryFiles("/test/set1/"), nullValue());
        assertThat(backupContentsAccessTooEarly.directoryFiles("/whatever"), nullValue());

        assertThat(backupContentsAccessTooEarlyIncludeDeleted.directoryFiles("/"), nullValue());
        assertThat(backupContentsAccessTooEarlyIncludeDeleted.directoryFiles("/test/"), nullValue());
        assertThat(backupContentsAccessTooEarlyIncludeDeleted.directoryFiles("/test/set1/"), nullValue());
        assertThat(backupContentsAccessTooEarlyIncludeDeleted.directoryFiles("/whatever"), nullValue());
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

        assertThat(backupContentsAccessNowIncludeDeleted.directoryFiles(""), Is.is(newFileSet(file("/dir1/"), file("/dir2/"), file("/test/"))));
        assertThat(backupContentsAccessNowIncludeDeleted.directoryFiles("/dir1"), Is.is(newFileSet(file("/dir1/fileDeleted", 2L))));
        assertThat(backupContentsAccessNowIncludeDeleted.directoryFiles("/test"), Is.is(newFileSet(file("/test/set1/", 3L), file("/test/set2/"))));
        assertThat(backupContentsAccessNowIncludeDeleted.directoryFiles("/test/set1"), Is.is(newFileSet(file("/test/set1/dir/"), file("/test/set1/file1", 4L), file("/test/set1/file2", 4L))));
        assertThat(backupContentsAccessNowIncludeDeleted.directoryFiles("/whatever"), nullValue());
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
            return BackupFile.builder().path(path).added(duration).build();
        return BackupFile.builder().path(path).added(duration).lastChanged(duration).length(duration).build();
    }
}