package com.underscoreresearch.backup.manifest.implementation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.nullValue;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        Map<String, List<BackupDirectory>> directories = new HashMap<>();
        Map<String, List<BackupFile>> files = new HashMap<>();

        directories.put("", Lists.newArrayList(
                new BackupDirectory("", 2L, Sets.newTreeSet(Lists.newArrayList("/test/set1/", "/")), null),
                new BackupDirectory("", 3L, Sets.newTreeSet(Lists.newArrayList("/test/set1/", "/test/set2/", "/")), null)
        ));

        directories.put("/", Lists.newArrayList(
                new BackupDirectory("/", 2L, Sets.newTreeSet(Lists.newArrayList("dir1/", "dir2/")), null),
                new BackupDirectory("/", 3L, Sets.newTreeSet(Lists.newArrayList("dir2/")), null)));

        directories.put("/dir1/", Lists.newArrayList(
                new BackupDirectory("/dir1/", 2L, Sets.newTreeSet(Lists.newArrayList("fileDeleted")), null)));

        directories.put("/dir2/", Lists.newArrayList(
                new BackupDirectory("/dir2/", 2L, Sets.newTreeSet(), null)));

        directories.put("/test/set1/", Lists.newArrayList(
                new BackupDirectory("/test/set1/", 2L, Sets.newTreeSet(Lists.newArrayList("file1", "dir/")), null),
                new BackupDirectory("/test/set1/", 3L, Sets.newTreeSet(Lists.newArrayList("file1", "file2", "dir/")), null)
        ));

        directories.put("/test/set1/dir/", Lists.newArrayList(
                new BackupDirectory("/test/set/dir/", 2L, Sets.newTreeSet(Lists.newArrayList("doh")), null)
        ));

        files.put("/test/set1/file1", Lists.newArrayList(
                BackupFile.builder().path("/test/set1/file1").added(2L).lastChanged(2L).length(2L).build(),
                BackupFile.builder().path("/test/set1/file1").added(4L).lastChanged(4L).length(4L).build()));

        files.put("/dir1/fileDeleted", Lists.newArrayList(
                BackupFile.builder().path("/dir1/fileDeleted").added(2L).lastChanged(2L).length(2L).build()));

        files.put("/test/set1/file2", Lists.newArrayList(
                BackupFile.builder().path("/test/set1/file2").added(4L).lastChanged(4L).length(4L).build()));

        Mockito.when(metadataRepository.directory(Mockito.anyString(), Mockito.any(), Mockito.anyBoolean())).then((t) -> {
            String path = t.getArgument(0);
            Long added = t.getArgument(1);
            boolean accumulative = t.getArgument(2);

            List<BackupDirectory> dirs = directories.get(path);
            BackupDirectory ret = null;
            if (dirs != null)
                for (int i = dirs.size() - 1; i >= 0; i--) {
                    if (added == null || dirs.get(i).getAdded() <= added) {
                        if (!accumulative) {
                            return dirs.get(i);
                        }
                        if (ret == null) {
                            ret = dirs.get(i);
                        } else {
                            ret.getFiles().addAll(dirs.get(i).getFiles());
                        }
                    }
                }
            return ret;
        });

        Mockito.when(metadataRepository.file(Mockito.anyString(), Mockito.any())).then((t) -> {
            String path = t.getArgument(0);
            Long added = t.getArgument(1);

            List<BackupFile> pathFiles = files.get(path);
            if (pathFiles != null) {
                if (added == null) {
                    if (pathFiles.size() > 0) {
                        return pathFiles.get(pathFiles.size() - 1);
                    }
                }
                for (int i = pathFiles.size() - 1; i >= 0; i--) {
                    if (pathFiles.get(i).getAdded() <= added)
                        return pathFiles.get(i);
                }
            }
            return null;
        });

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
        assertThat(backupContentsAccessEarly.directoryFiles("/"), Is.is(newFileSet(file("/dir1/", 2L), file("/test/"))));
        assertThat(backupContentsAccessEarly.directoryFiles("/test/"), Is.is(newFileSet(file("/test/set1/", 2L))));
        assertThat(backupContentsAccessEarly.directoryFiles("/test/set1/"), Is.is(newFileSet(file("/test/set1/dir/", 2L), file("/test/set1/file1", 2L))));
        assertThat(backupContentsAccessEarly.directoryFiles("/whatever"), nullValue());

        assertThat(backupContentsAccessEarlyIncludeDeleted.directoryFiles("/"), Is.is(newFileSet(file("/dir1/", 2L), file("/test/"))));
        assertThat(backupContentsAccessEarlyIncludeDeleted.directoryFiles("/test/"), Is.is(newFileSet(file("/test/set1/", 2L))));
        assertThat(backupContentsAccessEarlyIncludeDeleted.directoryFiles("/test/set1/"), Is.is(newFileSet(file("/test/set1/dir/", 2L), file("/test/set1/file1", 2L))));
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
        assertThat(backupContentsAccessCurrent.directoryFiles("/"), Is.is(newFileSet(file("/test/"))));
        assertThat(backupContentsAccessCurrent.directoryFiles("/test/"), Is.is(newFileSet(file("/test/set1/", 3L), file("/test/set2/"))));
        assertThat(backupContentsAccessCurrent.directoryFiles("/test/set1/"), Is.is(newFileSet(file("/test/set1/dir/", 2L), file("/test/set1/file1", 4L), file("/test/set1/file2", 4L))));
        assertThat(backupContentsAccessCurrent.directoryFiles("/whatever"), nullValue());

        assertThat(backupContentsAccess.directoryFiles(""), Is.is(newFileSet(file("/test/"))));
        assertThat(backupContentsAccess.directoryFiles("/test"), Is.is(newFileSet(file("/test/set1/", 3L), file("/test/set2/"))));
        assertThat(backupContentsAccess.directoryFiles("/test/set1"), Is.is(newFileSet(file("/test/set1/dir/", 2L), file("/test/set1/file1", 4L), file("/test/set1/file2", 4L))));
        assertThat(backupContentsAccess.directoryFiles("/whatever"), nullValue());

        assertThat(backupContentsAccessNowIncludeDeleted.directoryFiles(""), Is.is(newFileSet(file("/dir1/", 2L), file("/test/"))));
        assertThat(backupContentsAccessNowIncludeDeleted.directoryFiles("/dir1"), Is.is(newFileSet(file("/dir1/fileDeleted", 2L))));
        assertThat(backupContentsAccessNowIncludeDeleted.directoryFiles("/test"), Is.is(newFileSet(file("/test/set1/", 3L), file("/test/set2/"))));
        assertThat(backupContentsAccessNowIncludeDeleted.directoryFiles("/test/set1"), Is.is(newFileSet(file("/test/set1/dir/", 2L), file("/test/set1/file1", 4L), file("/test/set1/file2", 4L))));
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