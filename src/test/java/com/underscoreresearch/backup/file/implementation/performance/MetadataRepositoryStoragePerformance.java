package com.underscoreresearch.backup.file.implementation.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.file.CloseableMap;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.file.MapSerializer;
import com.underscoreresearch.backup.file.MetadataRepositoryStorage;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupLocation;

@Slf4j
@RequiredArgsConstructor
public abstract class MetadataRepositoryStoragePerformance {
    private static final int SIZE = 0; // For an actual performance test set this to 1M
    private Path directory;
    private MetadataRepositoryStorage storage;
    private List<BackupBlockStorage> blockStorage;
    private List<BackupFilePart> locations;

    public static void deleteDir(File tempDir) {
        String[] entries = tempDir.list();
        if (entries != null)
            for (String s : entries) {
                File currentFile = new File(tempDir.getPath(), s);
                if (currentFile.isDirectory()) {
                    deleteDir(currentFile);
                }
                currentFile.delete();
            }
        tempDir.delete();
    }

    @BeforeEach
    public void setup() throws IOException {
        if (SIZE > 0) {
            directory = Files.createTempDirectory("performancetest");
            storage = createStorageEngine(directory);
            if (storage != null) {
                storage.open(false);
                List<String> listOfStrings = new ArrayList<>();
                for (int i = 0; i < 40; i++) {
                    listOfStrings.add(hash(i));
                }
                blockStorage = Lists.newArrayList(BackupBlockStorage.builder()
                        .parts(listOfStrings)
                        .build());
                locations = listOfStrings.stream().map(str -> BackupFilePart.builder().blockHash(str).build())
                        .collect(Collectors.toList());
            }
        }
    }

    protected abstract MetadataRepositoryStorage createStorageEngine(Path directory);

    public void blockPerformance(boolean large) throws IOException {
        if (storage != null) {
            Stopwatch watch = Stopwatch.createStarted();
            for (int i = 0; i < SIZE; i++) {
                if ((i + 1) % (SIZE / 10) == 0) {
                    storage.commit();
                }
                storage.addBlock(createTestBlock(i, large));
            }
            storage.commit();
            System.out.printf("%s: Created %s%s blocks: %.3f%n",
                    getClass().getSimpleName(), largeLabel(large), SIZE, watch.elapsed(TimeUnit.MILLISECONDS) / 1000.0);
            Random random = new Random();
            watch.reset();
            watch.start();
            for (int i = 0; i < SIZE; i++) {
                int val = Math.abs(random.nextInt()) % SIZE;
                assertNotNull(storage.block(hash(val)));
            }
            System.out.printf("%s: Random read %s%s blocks: %.3f%n",
                    getClass().getSimpleName(), largeLabel(large), SIZE, watch.elapsed(TimeUnit.MILLISECONDS) / 1000.0);
        }
    }

    private String largeLabel(boolean large) {
        return large ? "large " : "small ";
    }

    public void randomFilePerformance(boolean large) throws IOException {
        if (storage != null) {
            Stopwatch watch = Stopwatch.createStarted();
            for (int i = 0; i < SIZE; i++) {
                if ((i + 1) % (SIZE / 10) == 0) {
                    storage.commit();
                }
                storage.addFile(createTestFile(i, true, large));
            }
            storage.commit();
            System.out.printf("%s: Random created %s%s files: %.3f%n",
                    getClass().getSimpleName(), largeLabel(large), SIZE, watch.elapsed(TimeUnit.MILLISECONDS) / 1000.0);
            Random random = new Random();
            watch.reset();
            watch.start();
            for (int i = 0; i < SIZE; i++) {
                int val = Math.abs(random.nextInt()) % SIZE;
                assertNotNull(storage.file(hash(val), null));
            }
            System.out.printf("%s: Random read %s%s files: %.3f%n",
                    getClass().getSimpleName(), largeLabel(large), SIZE, watch.elapsed(TimeUnit.MILLISECONDS) / 1000.0);
        }
    }

    public void filePerformance(boolean large) throws IOException {
        if (storage != null) {
            Stopwatch watch = Stopwatch.createStarted();
            for (int i = 0; i < SIZE; i++) {
                if ((i + 1) % (SIZE / 10) == 0) {
                    storage.commit();
                }
                storage.addFile(createTestFile(i, false, large));
            }
            storage.commit();
            System.out.printf("%s: Sequential create %s%s files: %.3f%n",
                    getClass().getSimpleName(), largeLabel(large), SIZE, watch.elapsed(TimeUnit.MILLISECONDS) / 1000.0);
            watch.reset();
            watch.start();
            for (int i = 0; i < SIZE; i++) {
                assertNotNull(storage.file(String.format("%010d", i), null));
            }
            System.out.printf("%s: Sequential read %s%s files: %.3f%n",
                    getClass().getSimpleName(), largeLabel(large), SIZE, watch.elapsed(TimeUnit.MILLISECONDS) / 1000.0);
            watch.reset();
            watch.start();
            AtomicInteger count = new AtomicInteger();
            try (CloseableStream<BackupFile> files = storage.allFiles(true)) {
                assertEquals(SIZE, files.stream().map(item -> {
                    try {
                        storage.addFile(item);
                        if (count.incrementAndGet() % (SIZE / 10) == 0) {
                            storage.commit();
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return item;
                }).count());
            }
            System.out.printf("%s: Stream read and update %s%s files: %.3f%n",
                    getClass().getSimpleName(), largeLabel(large), SIZE, watch.elapsed(TimeUnit.MILLISECONDS) / 1000.0);
            watch.reset();
            watch.start();
            try (CloseableStream<BackupFile> files = storage.allFiles(true)) {
                assertEquals(SIZE, files.stream().count());
            }
            System.out.printf("%s: Stream read %s%s files: %.3f%n",
                    getClass().getSimpleName(), largeLabel(large), SIZE, watch.elapsed(TimeUnit.MILLISECONDS) / 1000.0);
        }
    }

    public void temporaryTable(boolean large) throws IOException {
        if (storage != null) {
            try (CloseableMap<String, String> map = storage.temporaryMap(new MapSerializer<String, String>() {
                @Override
                public byte[] encodeKey(String s) {
                    return s.getBytes(StandardCharsets.UTF_8);
                }

                @Override
                public byte[] encodeValue(String s) {
                    return s.getBytes(StandardCharsets.UTF_8);
                }

                @Override
                public String decodeValue(byte[] data) {
                    return new String(data, StandardCharsets.UTF_8);
                }
            })) {
                Stopwatch watch = Stopwatch.createStarted();
                for (int i = 0; i < SIZE; i++) {
                    map.put(hash(i), createTempPayload(large, i));
                }
                storage.commit();
                System.out.printf("%s: Created %s%s temporary map: %.3f%n",
                        getClass().getSimpleName(), largeLabel(large), SIZE, watch.elapsed(TimeUnit.MILLISECONDS) / 1000.0);
                Random random = new Random();
                watch.reset();
                watch.start();
                for (int i = 0; i < SIZE; i++) {
                    int val = Math.abs(random.nextInt()) % SIZE;
                    assertNotNull(map.get(hash(i)));
                }
                System.out.printf("%s: Random read %s%s temporary map: %.3f%n",
                        getClass().getSimpleName(), largeLabel(large), SIZE, watch.elapsed(TimeUnit.MILLISECONDS) / 1000.0);
            }

        }
    }

    private String createTempPayload(boolean large, int i) {
        String ret = hash(i);
        if (large) {
            return hash(i) + hash(i + 1) + hash(i + 2) + hash(i + 3) + hash(i + 4) +
                    hash(i + 5) + hash(i + 6) + hash(i + 6) + hash(i + 7) + hash(i + 8);
        }
        return ret;
    }

    private BackupFile createTestFile(int i, boolean hash, boolean large) {
        return BackupFile.builder().path(hash ? hash(i) : String.format("%010d", i))
                .added((long) i)
                .locations(Lists.newArrayList(BackupLocation.builder()
                        .parts(large ? locations : null)
                        .build()))
                .lastChanged((long) i).build();
    }

    private BackupBlock createTestBlock(int i, boolean large) {
        return BackupBlock.builder().hash(hash(i)).storage(large ? blockStorage : null).build();
    }

    public String hash(int i) {
        return Hash.hash((String.valueOf(i)).getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (storage != null) {
            storage.close();
            deleteDir(directory.toFile());
        }
    }

    @Test
    public void largeBlockPerformance() throws IOException {
        blockPerformance(true);
    }

    @Test
    public void largeRandomFilePerformance() throws IOException {
        randomFilePerformance(true);
    }

    @Test
    public void largeFilePerformance() throws IOException {
        filePerformance(true);
    }

    @Test
    public void largeTemporaryTablePerformance() throws IOException {
        temporaryTable(true);
    }

    @Test
    public void smallBlockPerformance() throws IOException {
        blockPerformance(false);
    }

    @Test
    public void smallRandomFilePerformance() throws IOException {
        randomFilePerformance(false);
    }

    @Test
    public void smallFilePerformance() throws IOException {
        filePerformance(false);
    }

    @Test
    public void smallTemporaryTablePerformance() throws IOException {
        temporaryTable(false);
    }
}