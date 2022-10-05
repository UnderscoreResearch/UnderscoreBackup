package com.underscoreresearch.backup.file.implementation;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Date;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupActiveFile;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupActiveStatus;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupLocation;
import com.underscoreresearch.backup.model.BackupPendingSet;

class MapdbMetadataRepositoryTest {
    private static final String PATH = PATH_SEPARATOR + "test" + PATH_SEPARATOR + "path";
    private static final String PART_HASH = "part";
    private static final String HASH = "hash";

    private MapdbMetadataRepository repository;
    private File tempDir;
    private BackupFile backupFile;
    private BackupFilePart filePart;
    private BackupBlock backupBlock;

    @BeforeEach
    public void setup() throws IOException {
        tempDir = Files.createTempDirectory("test").toFile();
        repository = new MapdbMetadataRepository(tempDir.getPath(), false);
        repository.open(false);

        filePart = BackupFilePart.builder()
                .blockHash(HASH).blockIndex(0).partHash(PART_HASH)
                .build();
        backupFile = BackupFile.builder().lastChanged(Instant.EPOCH.toEpochMilli())
                .path(PATH)
                .locations(Lists.newArrayList(BackupLocation.builder().creation(Instant.now().toEpochMilli()).parts(
                                Lists.newArrayList(filePart))
                        .build()))
                .build();

        backupBlock = BackupBlock.builder()
                .format("ZIP")
                .created(Instant.now().toEpochMilli())
                .hash(HASH)
                .storage(Lists.newArrayList(
                        BackupBlockStorage.builder()
                                .destination("dest")
                                .encryption("AES256")
                                .ec("SR")
                                .parts(Lists.newArrayList("key1", "key2"))
                                .build()))
                .build();
    }

    @Test
    public void testFileSimple() throws IOException {
        assertNull(repository.existingFilePart(HASH));
        assertNull(repository.file(PATH));
        assertNull(repository.lastFile(PATH));

        repository.addFile(backupFile);

        backupFile.setAdded(backupFile.getLastChanged());
        assertThat(repository.file(PATH).get(0), Is.is(backupFile));
        assertThat(repository.existingFilePart(PART_HASH).get(0), Is.is(filePart));

        backupFile.setAdded(backupFile.getLastChanged() + 1);
        filePart.setBlockHash("otherhash");

        repository.addFile(backupFile);
        repository.addFile(backupFile);
        assertThat(repository.file(PATH).size(), Is.is(2));
        assertThat(repository.lastFile(PATH), Is.is(backupFile));
        assertThat(repository.existingFilePart(PART_HASH).size(), Is.is(2));

        repository.deleteFile(backupFile);
        repository.deleteFilePart(filePart);
        assertThat(repository.file(PATH).size(), Is.is(1));
        assertThat(repository.existingFilePart(PART_HASH).size(), Is.is(1));
    }

    @Test
    public void testBlock() throws IOException {
        assertNull(repository.block(HASH));

        repository.addBlock(backupBlock);
        assertThat(repository.block(HASH), Is.is(backupBlock));

        repository.deleteBlock(backupBlock);
        assertNull(repository.block(HASH));
    }

    @Test
    public void testClear() throws IOException {
        assertNull(repository.block(HASH));

        repository.addBlock(backupBlock);
        assertThat(repository.block(HASH), Is.is(backupBlock));
        repository.clear();

        assertNull(repository.block(HASH));
    }

    @Test
    public void testDirectory() throws IOException {
        assertNull(repository.directory(PATH));

        Long timestamp = Instant.now().toEpochMilli();
        repository.addDirectory(new BackupDirectory(PATH, timestamp, Sets.newTreeSet(Lists.newArrayList("a, b"))));
        assertThat(repository.lastDirectory(PATH), Is.is(new BackupDirectory(PATH, timestamp, Sets.newTreeSet(Lists.newArrayList("a, b")))));
        assertThat(repository.directory(PATH), Is.is(Lists.newArrayList(new BackupDirectory(PATH, timestamp, Sets.newTreeSet(Lists.newArrayList("a, b"))))));

        repository.deleteDirectory(PATH, timestamp);
        assertNull(repository.directory(PATH));
    }

    @Test
    public void testActivePath() throws IOException {
        assertThat(repository.getActivePaths(null).size(), Is.is(0));

        BackupActivePath p1 = new BackupActivePath("a",
                Sets.newHashSet(new BackupActiveFile("a"),
                        new BackupActiveFile("b")));
        BackupActivePath p2 = new BackupActivePath("a",
                Sets.newHashSet(new BackupActiveFile("c"),
                        new BackupActiveFile("d")));
        p1.setSetIds(Lists.newArrayList("s1"));
        p2.setSetIds(Lists.newArrayList("s1"));

        repository.pushActivePath("s1", PATH_SEPARATOR + "a", p1);
        repository.pushActivePath("s2", PATH_SEPARATOR + "a", p1);
        repository.pushActivePath("s1", PATH_SEPARATOR + "a" + PATH_SEPARATOR + "b", p2);

        assertThat(repository.getActivePaths("s1"), Is.is(ImmutableMap.of(PATH_SEPARATOR + "a", p1,
                PATH_SEPARATOR + "a" + PATH_SEPARATOR + "b", p2)));

        p1.setSetIds(Lists.newArrayList("s2", "s1"));
        assertThat(repository.getActivePaths(null), Is.is(ImmutableMap.of(PATH_SEPARATOR + "a", p1,
                PATH_SEPARATOR + "a" + PATH_SEPARATOR + "b", p2)));

        p1.setSetIds(Lists.newArrayList("s1"));
        repository.popActivePath("s1", PATH_SEPARATOR + "a" + PATH_SEPARATOR + "b");
        assertThat(repository.getActivePaths("s1"), Is.is(ImmutableMap.of(PATH_SEPARATOR + "a",
                p1)));
    }

    @Test
    public void testActivePathMerge() throws IOException {
        BackupActivePath p1 = new BackupActivePath("a",
                Sets.newHashSet(
                        BackupActiveFile.builder().path("a").status(BackupActiveStatus.INCOMPLETE).build(),
                        BackupActiveFile.builder().path("b").status(BackupActiveStatus.EXCLUDED).build(),
                        BackupActiveFile.builder().path("c").status(BackupActiveStatus.INCLUDED).build(),
                        BackupActiveFile.builder().path("d").status(BackupActiveStatus.EXCLUDED).build()));
        BackupActivePath p2 = new BackupActivePath("a",
                Sets.newHashSet(
                        BackupActiveFile.builder().path("a").status(BackupActiveStatus.EXCLUDED).build(),
                        BackupActiveFile.builder().path("b").status(BackupActiveStatus.INCLUDED).build(),
                        BackupActiveFile.builder().path("e").status(BackupActiveStatus.EXCLUDED).build()));

        repository.pushActivePath("s1", PATH_SEPARATOR + "a", p1);
        repository.pushActivePath("s2", PATH_SEPARATOR + "a", p2);

        BackupActivePath combinedActivePath = new BackupActivePath("a",
                Sets.newHashSet(
                        BackupActiveFile.builder().path("a").status(BackupActiveStatus.INCOMPLETE).build(),
                        BackupActiveFile.builder().path("b").status(BackupActiveStatus.INCLUDED).build(),
                        BackupActiveFile.builder().path("c").status(BackupActiveStatus.INCLUDED).build(),
                        BackupActiveFile.builder().path("d").status(BackupActiveStatus.EXCLUDED).build(),
                        BackupActiveFile.builder().path("e").status(BackupActiveStatus.EXCLUDED).build()));
        combinedActivePath.setSetIds(Lists.newArrayList("s2", "s1"));
        assertThat(repository.getActivePaths(null), Is.is(
                ImmutableMap.of(PATH_SEPARATOR + "a",
                        combinedActivePath)));
    }

    @Test
    public void deleteNonExisting() throws IOException {
        repository.deleteFile(backupFile);
        repository.deleteFilePart(filePart);
        repository.deleteBlock(backupBlock);
        repository.popActivePath("s1", "whatever");
    }

    @Test
    public void testDeleteBlocks() throws IOException {
        for (int i = 0; i < 10000; i++) {
            repository.addBlock(BackupBlock.builder().hash(i + "").build());
        }

        try (CloseableLock ignored = repository.acquireLock()) {
            repository.allBlocks().filter(t -> Integer.parseInt(t.getHash()) % 2 == 0).forEach(t -> {
                try {
                    repository.deleteBlock(t);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            assertThat(repository.allBlocks().count(), Is.is(5000L));
        }
    }

    @Test
    public void testPendingSet() throws IOException {
        BackupPendingSet set1 = BackupPendingSet.builder().setId("1").schedule("abc").scheduledAt(new Date(1000)).build();
        BackupPendingSet set2 = BackupPendingSet.builder().setId("2").schedule("def").scheduledAt(new Date(1500)).build();
        BackupPendingSet set3 = BackupPendingSet.builder().setId("3").schedule("ghi").scheduledAt(new Date(2000)).build();
        BackupPendingSet set1_1 = BackupPendingSet.builder().setId("1").schedule("klm").scheduledAt(new Date(2500)).build();
        repository.addPendingSets(set1);
        repository.addPendingSets(set2);
        repository.addPendingSets(set3);

        assertThat(repository.getPendingSets(), Is.is(Sets.newHashSet(set1, set2, set3)));
        repository.addPendingSets(set1_1);
        assertThat(repository.getPendingSets(), Is.is(Sets.newHashSet(set1_1, set2, set3)));
        repository.deletePendingSets("1");
        assertThat(repository.getPendingSets(), Is.is(Sets.newHashSet(set2, set3)));
    }

    @AfterEach
    public void teardown() throws IOException {
        repository.close();
        String[] entries = tempDir.list();
        for (String s : entries) {
            File currentFile = new File(tempDir.getPath(), s);
            currentFile.delete();
        }
        tempDir.delete();
    }
}
