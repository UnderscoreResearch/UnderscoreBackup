package com.underscoreresearch.backup.file.implementation;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.file.implementation.LockingMetadataRepository.MINIMUM_WAIT_UPDATE_MS;
import static com.underscoreresearch.backup.file.implementation.performance.MetadataRepositoryStoragePerformance.deleteDir;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.hamcrest.Matchers;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.underscoreresearch.backup.file.CloseableMap;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.file.MapSerializer;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupActiveFile;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupActiveStatus;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockAdditional;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupLocation;
import com.underscoreresearch.backup.model.BackupPartialFile;
import com.underscoreresearch.backup.model.BackupPendingSet;
import com.underscoreresearch.backup.model.BackupUpdatedFile;
import com.underscoreresearch.backup.model.ExternalBackupFile;

abstract class LockingMetadataRepositoryTest {
    private static final String PATH = PATH_SEPARATOR + "test" + PATH_SEPARATOR + "path" +
            PATH_SEPARATOR + "test" + PATH_SEPARATOR + "path";
    private static final String LARGE_PATH = PATH + PATH + PATH + PATH + PATH + PATH + PATH + PATH +
            PATH + PATH + PATH + PATH + PATH + PATH + PATH + PATH +
            PATH + PATH + PATH + PATH + PATH + PATH + PATH + PATH +
            PATH + PATH + PATH + PATH + PATH + PATH + PATH + PATH +
            PATH + PATH + PATH + PATH + PATH + PATH + PATH + PATH;
    private static final String PART_HASH = "part";
    private static final String HASH = "hash";
    private static final int MAX_ITEMS = 100;
    protected LockingMetadataRepository repository;
    private File tempDir;
    private BackupFile backupFile;
    private BackupFilePart filePart;
    private BackupBlock backupBlock;

    @BeforeEach
    public void setup() throws IOException {
        tempDir = Files.createTempDirectory("test").toFile();
        repository = createRepository(tempDir);
        if (repository != null) {
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
    }

    protected abstract LockingMetadataRepository createRepository(File file);

    protected void halfwayUpgrade() throws IOException {
    }

    @Test
    public void testFile() throws IOException {
        testFile(PATH);
    }

    private void testFile(String path) throws IOException {
        if (repository == null) {
            return;
        }
        assertNull(repository.existingFilePart(PART_HASH));
        assertNull(repository.file(path));
        assertNull(repository.file(path, null));

        backupFile.setPath(path);
        repository.addFile(backupFile);

        backupFile.setAdded(backupFile.getLastChanged());
        assertThat(repository.file(path).get(0), Is.is(new ExternalBackupFile(backupFile)));
        assertThat(repository.existingFilePart(PART_HASH).get(0), Is.is(filePart));

        backupFile.setAdded(backupFile.getLastChanged() + 1);
        filePart.setBlockHash("otherhash");

        repository.addFile(backupFile);
        repository.addFile(backupFile);
        assertThat(repository.file(path).size(), Is.is(2));
        assertThat(repository.file(path, null), Is.is(backupFile));
        assertThat(repository.existingFilePart(PART_HASH).size(), Is.is(2));

        backupFile.setAdded(backupFile.getLastChanged());
        filePart.setBlockHash("hash");
        assertThat(repository.file(path, backupFile.getLastChanged()), Is.is(backupFile));

        halfwayUpgrade();

        assertThat(repository.getFileCount(), Is.is(2L));
        repository.deleteFile(backupFile);
        repository.deleteFilePart(filePart);
        assertThat(repository.file(path).size(), Is.is(1));
        assertThat(repository.existingFilePart(PART_HASH).size(), Is.is(1));

        assertThat(repository.getFileCount(), Is.is(1L));
        assertThat(repository.getPartCount(), Is.is(1L));
    }

    @Test
    public void testFileLarge() throws IOException {
        testFile(LARGE_PATH);
    }

    @Test
    public void testIterationDeleteDescend() throws IOException {
        testIterationDelete(false);
    }

    @Test
    public void testIterationDeleteAscend() throws IOException {
        testIterationDelete(true);
    }

    private void testIterationDelete(boolean ascending) throws IOException {
        if (repository == null) {
            return;
        }
        final int PATHS = 100;
        final int VERSIONS = 100;

        for (int i = 0; i < PATHS; i++) {
            for (long j = 0; j < VERSIONS; j++) {
                repository.addFile(BackupFile.builder().path(String.format("%010d", i)).added(j).build());
            }
        }

        try (CloseableStream<BackupFile> files = repository.allFiles(ascending)) {
            assertThat(repository.getFileCount(), Is.is((long) PATHS * VERSIONS));
            AtomicInteger counter = new AtomicInteger(0);
            files.stream().forEach((file) -> {
                int count = counter.get();
                if (counter.incrementAndGet() % PATHS == 0) {
                    repository.commit();
                }
                if (!ascending) {
                    count = PATHS * VERSIONS - count - 1;
                }
                assertThat(Integer.parseInt(file.getPath()), Is.is(count / VERSIONS));
                assertThat(file.getAdded(), Is.is((long) count % VERSIONS));
                try {
                    repository.deleteFile(file);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            assertThat(counter.get(), Is.is(PATHS * VERSIONS));
            assertThat(repository.getFileCount(), Is.is(0L));
        }
    }

    @Test
    public void testBlock() throws IOException {
        if (repository == null) {
            return;
        }
        assertNull(repository.block(HASH));

        repository.addBlock(backupBlock);
        assertThat(repository.block(HASH), Is.is(backupBlock));

        halfwayUpgrade();

        assertThat(repository.getBlockCount(), Is.is(1L));

        repository.deleteBlock(backupBlock);
        assertNull(repository.block(HASH));
    }

    @Test
    public void testClear() throws IOException {
        if (repository == null) {
            return;
        }
        assertNull(repository.block(HASH));

        repository.addBlock(backupBlock);
        assertThat(repository.block(HASH), Is.is(backupBlock));
        repository.clear();

        assertNull(repository.block(HASH));
        assertThat(repository.getBlockCount(), Is.is(0L));
    }

    @Test
    public void testDirectory() throws IOException {
        testDirectory(PATH);
    }

    @Test
    public void testLargeDirectory() throws IOException {
        testDirectory(LARGE_PATH);
    }

    private void testDirectory(String path) throws IOException {
        if (repository == null) {
            return;
        }
        assertNull(repository.directory(path, null, false));

        long timestamp = Instant.now().toEpochMilli();
        repository.addDirectory(new BackupDirectory(path, timestamp, Sets.newTreeSet(Lists.newArrayList("a, b"))));
        repository.addDirectory(new BackupDirectory(path, timestamp + 1, Sets.newTreeSet(Lists.newArrayList("b", "c"))));
        repository.addDirectory(new BackupDirectory(path, timestamp + 2, Sets.newTreeSet(Lists.newArrayList("c", "d"))));
        assertThat(repository.directory(path, timestamp, false), Is.is(new BackupDirectory(path, timestamp, Sets.newTreeSet(Lists.newArrayList("a, b")))));
        assertThat(repository.directory(path, null, false), Is.is(new BackupDirectory(path, timestamp + 2, Sets.newTreeSet(Lists.newArrayList("c", "d")))));
        assertThat(repository.directory(path, null, true), Is.is(new BackupDirectory(path, timestamp + 2, Sets.newTreeSet(Lists.newArrayList("a, b", "b", "c", "d")))));
        assertThat(repository.directory(path, timestamp + 1, true), Is.is(new BackupDirectory(path, timestamp + 1, Sets.newTreeSet(Lists.newArrayList("a, b", "b", "c")))));
        assertThat(repository.getDirectoryCount(), Is.is(3L));

        halfwayUpgrade();

        repository.deleteDirectory(path, timestamp + 2);
        repository.deleteDirectory(path, timestamp + 1);
        repository.deleteDirectory(path, timestamp);
        assertNull(repository.directory(path, null, false));
        assertThat(repository.getDirectoryCount(), Is.is(0L));
    }

    @Test
    public void testActivePath() throws IOException {
        if (repository == null) {
            return;
        }
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

        assertTrue(repository.hasActivePath("s1", PATH_SEPARATOR + "a"));
        assertFalse(repository.hasActivePath("s1", PATH_SEPARATOR + "ab"));
    }

    @Test
    public void testActivePathMerge() throws IOException {
        if (repository == null) {
            return;
        }
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
        if (repository == null) {
            return;
        }
        repository.deleteFile(backupFile);
        repository.deleteFilePart(filePart);
        repository.deleteBlock(backupBlock);
        repository.popActivePath("s1", "whatever");
    }

    @Test
    public void testDeleteBlocks() throws IOException {
        if (repository == null) {
            return;
        }
        for (int i = 0; i < MAX_ITEMS; i++) {
            repository.addBlock(BackupBlock.builder().hash(i + "").build());
        }

        halfwayUpgrade();

        try (CloseableStream<BackupBlock> blocks = repository.allBlocks()) {
            blocks.stream().filter(t -> Integer.parseInt(t.getHash()) % 2 == 0).forEach(t -> {
                try {
                    repository.deleteBlock(t);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
        try (CloseableStream<BackupBlock> blocks = repository.allBlocks()) {
            assertThat(blocks.stream().count(), Is.is((long) MAX_ITEMS / 2));
        }
    }

    @Test
    public void testPendingSet() throws IOException {
        if (repository == null) {
            return;
        }
        BackupPendingSet set0 = BackupPendingSet.builder().setId("").schedule("abc").scheduledAt(new Date(1000)).build();
        BackupPendingSet set1 = BackupPendingSet.builder().setId("1").schedule("abc").scheduledAt(new Date(1000)).build();
        BackupPendingSet set2 = BackupPendingSet.builder().setId("2").schedule("def").scheduledAt(new Date(1500)).build();
        BackupPendingSet set3 = BackupPendingSet.builder().setId("3").schedule("ghi").scheduledAt(new Date(2000)).build();
        BackupPendingSet set1_1 = BackupPendingSet.builder().setId("1").schedule("klm").scheduledAt(new Date(2500)).build();
        repository.addPendingSets(set0);
        repository.addPendingSets(set1);
        repository.addPendingSets(set2);
        repository.addPendingSets(set3);

        halfwayUpgrade();

        assertThat(repository.getPendingSets(), Is.is(Sets.newHashSet(set0, set1, set2, set3)));
        repository.addPendingSets(set1_1);
        assertThat(repository.getPendingSets(), Is.is(Sets.newHashSet(set0, set1_1, set2, set3)));
        repository.deletePendingSets("1");

        repository.flushLogging();
        repository.close();

        repository = createRepository(tempDir);
        repository.open(false);
        assertThat(repository.getPendingSets(), Is.is(Sets.newHashSet(set0, set2, set3)));
    }

    @Test
    public void testPartialFiles() throws IOException {
        if (repository == null) {
            return;
        }
        BackupPartialFile partialFile1 = new BackupPartialFile(backupFile);
        partialFile1.addPart(repository, new BackupPartialFile.PartialCompletedPath(1L,
                BackupFilePart.builder().blockHash("hash").partHash("part").build()));

        BackupFile backupFile2 = BackupFile.builder().lastChanged(Instant.EPOCH.toEpochMilli())
                .path(PATH + "2")
                .locations(Lists.newArrayList(BackupLocation.builder().creation(Instant.now().toEpochMilli()).parts(
                                Lists.newArrayList(filePart))
                        .build()))
                .build();
        BackupPartialFile partialFile2 = new BackupPartialFile(backupFile2);
        partialFile2.addPart(repository, new BackupPartialFile.PartialCompletedPath(1L,
                BackupFilePart.builder().blockHash("hash").partHash("part").build()));

        repository.savePartialFile(partialFile1);
        repository.savePartialFile(partialFile2);

        assertThat(repository.getPartialFile(new BackupPartialFile(BackupFile.builder().path(PATH).build())),
                Is.is(partialFile1));
        assertThat(repository.getPartialFile(new BackupPartialFile(BackupFile.builder().path(PATH + "2").build())),
                Is.is(partialFile2));
        repository.deletePartialFile(partialFile2);

        assertNull(repository.getPartialFile(partialFile2));
        repository.clearPartialFiles();
        assertNull(repository.getPartialFile(partialFile1));
    }

    @Test
    public void testSwitchingBlocks() throws IOException {
        if (repository == null) {
            return;
        }
        repository.addTemporaryBlock(backupBlock);
        repository.installTemporaryBlocks();
        assertThat(repository.block(backupBlock.getHash()), Is.is(backupBlock));
        repository.close();

        repository = createRepository(tempDir);
        repository.open(false);
        repository.addTemporaryBlock(backupBlock);
        assertThat(repository.block(backupBlock.getHash()), Is.is(backupBlock));
    }

    @Test
    public void testAdditionalBlocks() throws IOException {
        if (repository == null) {
            return;
        }
        BackupBlockAdditional ba1 = BackupBlockAdditional.builder().publicKey("p1").hash("h1").used(true).properties(Lists.newArrayList(ImmutableMap.of("a", "b"))).build();
        BackupBlockAdditional ba2 = BackupBlockAdditional.builder().publicKey("p1").hash("h2").used(true).properties(Lists.newArrayList(ImmutableMap.of("a", "b"))).build();
        BackupBlockAdditional ba3 = BackupBlockAdditional.builder().publicKey("p2").hash("h1").used(false).properties(Lists.newArrayList(ImmutableMap.of("a", "b"))).build();
        BackupBlockAdditional ba4 = BackupBlockAdditional.builder().publicKey("p2").hash("h2").used(false).properties(Lists.newArrayList(ImmutableMap.of("a", "b"))).build();
        repository.addAdditionalBlock(ba1);
        repository.addAdditionalBlock(ba2);
        repository.addAdditionalBlock(ba3);
        repository.addAdditionalBlock(ba4);

        halfwayUpgrade();

        assertThat(repository.additionalBlock("p1", "h1"), Is.is(ba1));
        assertThat(repository.additionalBlock("p2", "h1"), Is.is(ba3));

        repository.deleteAdditionalBlock("p1", "h1");
        repository.deleteAdditionalBlock("p2", null);
        assertNull(repository.additionalBlock("p1", "h1"));
        assertNull(repository.additionalBlock("p2", "h1"));
        assertNull(repository.additionalBlock("p2", "h2"));
        assertThat(repository.additionalBlock("p1", "h2"), Is.is(ba2));
    }

    @Test
    public void testUpdatedFiles() throws IOException {
        if (repository == null) {
            return;
        }
        long start = System.currentTimeMillis();
        backupFile = BackupFile.builder().lastChanged(System.currentTimeMillis())
                .path(PATH)
                .locations(Lists.newArrayList(BackupLocation.builder().creation(Instant.now().toEpochMilli()).parts(
                                Lists.newArrayList(filePart))
                        .build()))
                .build();
        repository.addFile(backupFile);
        repository.addFile(backupFile);
        BackupUpdatedFile f1 = BackupUpdatedFile.builder().path(PATH).lastUpdated(System.currentTimeMillis()).build();
        BackupUpdatedFile f2 = BackupUpdatedFile.builder().path("/p2").lastUpdated(System.currentTimeMillis()).build();
        BackupUpdatedFile f3 = BackupUpdatedFile.builder().path("/p2").lastUpdated(System.currentTimeMillis()).build();
        BackupUpdatedFile f4 = BackupUpdatedFile.builder().path("/p3").lastUpdated(System.currentTimeMillis()).build();
        BackupUpdatedFile f5 = BackupUpdatedFile.builder().path("/p3").lastUpdated(System.currentTimeMillis() + 20000L).build();
        BackupUpdatedFile f6 = BackupUpdatedFile.builder().path(LARGE_PATH).lastUpdated(System.currentTimeMillis()).build();

        repository.addUpdatedFile(f1, 1000);
        repository.addUpdatedFile(f2, 1000);
        repository.addUpdatedFile(f3, 1000);
        repository.addUpdatedFile(f4, 1000);
        repository.addUpdatedFile(f5, 1000);
        repository.addUpdatedFile(f6, 1000);

        halfwayUpgrade();

        repository.flushLogging();

        AtomicLong lastTime = new AtomicLong(0L);
        try (CloseableStream<BackupUpdatedFile> map = repository.getUpdatedFiles()) {
            long count = map.stream().map(item -> {
                assertThat(item.getLastUpdated(), Matchers.greaterThanOrEqualTo(lastTime.get()));
                lastTime.set(item.getLastUpdated());
                switch (item.getPath()) {
                    case PATH -> {
                        assertThat(item.getLastUpdated(), Matchers.greaterThanOrEqualTo(start + MINIMUM_WAIT_UPDATE_MS + 1000));
                        assertThat(item.getLastUpdated(), Matchers.lessThanOrEqualTo(System.currentTimeMillis() + MINIMUM_WAIT_UPDATE_MS + 1000));
                    }
                    case LARGE_PATH, "/p2" -> {
                        assertThat(item.getLastUpdated(), Matchers.greaterThanOrEqualTo(start + MINIMUM_WAIT_UPDATE_MS));
                        assertThat(item.getLastUpdated(), Matchers.lessThanOrEqualTo(System.currentTimeMillis() + MINIMUM_WAIT_UPDATE_MS));
                    }
                    case "/p3" -> {
                        assertThat(item.getLastUpdated(), Matchers.greaterThanOrEqualTo(start + 20000L + MINIMUM_WAIT_UPDATE_MS));
                        assertThat(item.getLastUpdated(), Matchers.lessThanOrEqualTo(System.currentTimeMillis() + 20000L + MINIMUM_WAIT_UPDATE_MS));
                    }
                    default -> throw new AssertionError("Unknown " + item.getPath());
                }
                try {
                    repository.removeUpdatedFile(item);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return item;
            }).filter(Objects::nonNull).count();
            assertThat(count, Is.is(4L));
        }

        try (CloseableStream<BackupUpdatedFile> map = repository.getUpdatedFiles()) {
            assertThat(map.stream().count(), Is.is(0L));
        }
    }

    @Test
    public void testTemporaryMap() throws IOException {
        if (repository == null) {
            return;
        }
        try (CloseableMap<String, Long> map = repository.temporaryMap(new MapSerializer<String, Long>() {
            @Override
            public byte[] encodeKey(String s) {
                return s.getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public byte[] encodeValue(Long val) {
                ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
                buffer.putLong(val);
                return buffer.array();
            }

            @Override
            public Long decodeValue(byte[] data) {
                return ByteBuffer.wrap(data).getLong();
            }
        })) {
            map.put("a", 1L);
            map.put("b", 2L);
            map.put("c", 3L);
            assertThat(map.get("c"), Is.is(3L));
            assertTrue(map.containsKey("c"));
            assertTrue(map.delete("c"));
            assertFalse(map.delete("c"));
            assertNull(map.get("c"));
        }
    }

    @AfterEach
    public void teardown() throws IOException {
        if (repository != null) {
            repository.close();
        }
        deleteDir(tempDir);
    }
}
