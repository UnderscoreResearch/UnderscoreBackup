package com.underscoreresearch.backup.block.assignments;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.block.BlockDownloader;
import com.underscoreresearch.backup.block.FileBlockUploader;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupCompletion;
import com.underscoreresearch.backup.model.BackupData;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.utils.state.MachineState;
import org.hamcrest.Matchers;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

class AssignmentTests {
    private BackupSet set;
    private FileSystemAccess access;
    private FileBlockUploader uploader;
    private Map<String, byte[]> uploadedData;
    private EncryptionIdentity encryptionKey;
    private String expectedFormat;
    private MetadataRepository repository;
    private boolean registerPart;

    @BeforeEach
    public void setup() throws IOException, GeneralSecurityException {
        set = new BackupSet();
        access = Mockito.mock(FileSystemAccess.class);
        uploader = Mockito.mock(FileBlockUploader.class);
        repository = Mockito.mock(MetadataRepository.class);
        encryptionKey = EncryptionIdentity.generateKeyWithPassword("password");
        uploadedData = new HashMap<>();
        registerPart = false;
        set.setDestinations(Lists.newArrayList("destination"));

        Mockito.when(repository.block(Mockito.anyString()))
                .thenAnswer((t) -> BackupBlock.builder()
                        .hash(t.getArgument(0))
                        .storage(Lists.newArrayList(BackupBlockStorage.builder().destination("destination").build()))
                        .build());

        Mockito.when(access.readData(anyString(), any(), anyLong(), anyInt())).then((t) -> {
            String file = t.getArgument(0);
            byte[] buffer = t.getArgument(1);
            long offset = t.getArgument(2);
            int length = t.getArgument(3);

            int size = Integer.parseInt(file);

            for (long i = offset; i < offset + length && i < size; i++) {
                buffer[(int) (i - offset)] = (byte) i;
            }

            int read = (int) (Math.min(offset + length, size) - offset);
            if (registerPart) {
                byte[] data = new byte[read];
                for (int i = 0; i < buffer.length; i++)
                    data[i] = buffer[i];

                Hash partHasher = new Hash();
                encryptionKey.addBlockHashSalt(partHasher);
                partHasher.addBytes(data);
                String hash = partHasher.getHash();
                Mockito.when(repository.existingFilePart(hash)).thenReturn(Lists
                        .newArrayList(BackupFilePart.builder().blockHash("block").blockIndex(read).build()));
            }

            return read;
        });

        Mockito.doAnswer((t) -> {
            BackupSet set = t.getArgument(0);
            BackupData unencryptedData = t.getArgument(1);
            String blockHash = t.getArgument(2);
            String format = t.getArgument(3);
            BackupCompletion completionFuture = t.getArgument(4);

            assertThat(format, Is.is(expectedFormat));
            assertNotNull(set);

            synchronized (uploadedData) {
                uploadedData.put(blockHash, unencryptedData.getData());
            }

            Thread thread = new Thread(() -> {
                try {
                    Thread.sleep((long) (Math.random() * 20));
                } catch (InterruptedException e) {
                }
                completionFuture.completed(true);
            });
            thread.setDaemon(true);
            thread.start();

            return null;
        }).when(uploader).uploadBlock(any(), any(), any(), any(), any());

    }

    @Test
    public void rawUpload() throws InterruptedException {
        RawLargeFileBlockAssignment largeFileBlockAssignment = new RawLargeFileBlockAssignment(uploader,
                Mockito.mock(BlockDownloader.class), access,
                Mockito.mock(MetadataRepository.class), new MachineState(false), encryptionKey, 50);
        expectedFormat = "RAW";

        AtomicBoolean failed = new AtomicBoolean();
        for (int i = 1; i <= 300; i++) {
            BackupFile file = BackupFile.builder().path(i + "").length((long) i).lastChanged((long) i).build();
            int size = i;
            assertThat(largeFileBlockAssignment.assignBlocks(set, file, (locations) -> {
                try {
                    synchronized (uploadedData) {
                        int index = 0;
                        for (BackupFilePart part : locations.get(0).getParts()) {
                            byte[] data = uploadedData.get(part.getBlockHash());
                            assertThat(data.length, Matchers.lessThanOrEqualTo(50));
                            for (int j = 0; j < data.length; j++) {
                                assertThat(data[j], Is.is((byte) index));
                                index++;
                            }
                        }
                        assertThat(index, Is.is(size));
                        assertThat(locations.size(), Matchers.lessThanOrEqualTo(6));
                    }
                } catch (Throwable exc) {
                    failed.set(true);
                    throw new RuntimeException(exc);
                }
            }), Is.is(true));
        }
        largeFileBlockAssignment.flushAssignments();

        Thread.sleep(100);
        assertFalse(failed.get());
    }

    @Test
    public void gzipUpload() throws InterruptedException {
        GzipLargeFileBlockAssignment largeFileBlockAssignment = new GzipLargeFileBlockAssignment(uploader,
                Mockito.mock(BlockDownloader.class), access,
                Mockito.mock(MetadataRepository.class), new MachineState(false), encryptionKey, 50);
        expectedFormat = "GZIP";

        AtomicBoolean failed = new AtomicBoolean();
        for (int i = 1; i <= 300; i++) {
            BackupFile file = BackupFile.builder().path(i + "").length((long) i).lastChanged((long) i).build();
            int size = i;
            assertThat(largeFileBlockAssignment.assignBlocks(set, file, (locations) -> {
                try {
                    synchronized (uploadedData) {
                        int index = 0;
                        for (BackupFilePart part : locations.get(0).getParts()) {
                            byte[] compressed = uploadedData.get(part.getBlockHash());
                            byte[] data = new byte[500];
                            int dataSize;
                            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(compressed)) {
                                try (GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
                                    dataSize = gzipInputStream.read(data, 0, data.length);
                                }
                            }
                            assertThat(dataSize, Matchers.lessThanOrEqualTo(50));
                            for (int j = 0; j < dataSize; j++) {
                                assertThat(data[j], Is.is((byte) index));
                                index++;
                            }
                        }
                        assertThat(index, Is.is(size));
                        assertThat(locations.size(), Is.is(1));
                        assertThat(locations.get(0).getParts().size(), Matchers.lessThanOrEqualTo(6));
                    }
                } catch (Throwable exc) {
                    failed.set(true);
                    throw new RuntimeException(exc);
                }
            }), Is.is(true));
        }
        largeFileBlockAssignment.flushAssignments();

        Thread.sleep(100);
        assertFalse(failed.get());
    }

    @Test
    public void zipUpload() throws InterruptedException {
        SmallFileBlockAssignment fileBlockAssignment = new ZipSmallBlockAssignment(uploader,
                Mockito.mock(BlockDownloader.class),
                repository, access, encryptionKey, 150, 300);
        expectedFormat = "ZIP";

        AtomicBoolean failed = new AtomicBoolean();
        for (int i = 1; i <= 300; i++) {
            BackupFile file = BackupFile.builder().path(i + "").length((long) i).lastChanged((long) i).build();
            int size = i;
            if (size <= 150) {
                assertThat(fileBlockAssignment.assignBlocks(set, file, (locations) -> {
                    try {
                        synchronized (uploadedData) {
                            int index = 0;
                            for (BackupFilePart part : locations.get(0).getParts()) {
                                byte[] compressed = uploadedData.get(part.getBlockHash());
                                byte[] data = new byte[500];
                                int dataSize = 0;
                                try (ZipInputStream inputStream = new ZipInputStream(new ByteArrayInputStream(compressed))) {
                                    ZipEntry ze;
                                    while ((ze = inputStream.getNextEntry()) != null) {
                                        if (Integer.parseInt(ze.getName()) == part.getBlockIndex()) {
                                            dataSize = inputStream.read(data);
                                            break;
                                        }
                                    }
                                }
                                assertThat(dataSize, Is.is(size));
                                for (int j = 0; j < dataSize; j++) {
                                    assertThat(data[j], Is.is((byte) index));
                                    index++;
                                }
                            }
                            assertThat(index, Is.is(size));
                            assertThat(locations.size(), Is.is(1));
                            assertThat(locations.get(0).getParts().size(), Is.is(1));
                        }
                    } catch (Throwable exc) {
                        failed.set(true);
                        throw new RuntimeException(exc);
                    }
                }), Is.is(true));
            } else {
                assertThat(fileBlockAssignment.assignBlocks(set, file, (doh) -> {
                }), Is.is(false));
            }
        }
        fileBlockAssignment.flushAssignments();

        Thread.sleep(100);
        assertFalse(failed.get());
    }

    @Test
    public void zipUploadExists() throws InterruptedException {
        SmallFileBlockAssignment smallBlockAssignment = new EncryptedSmallBlockAssignment(uploader,
                Mockito.mock(BlockDownloader.class),
                repository, access, encryptionKey, 150, 300);
        registerPart = true;

        AtomicBoolean failed = new AtomicBoolean();
        for (int i = 1; i <= 10; i++) {
            BackupFile file = BackupFile.builder().path(i + "").length((long) i).lastChanged((long) i).build();
            int size = i;
            assertThat(smallBlockAssignment.assignBlocks(set, file, (locations) -> {
                try {
                    synchronized (uploadedData) {
                        for (BackupFilePart part : locations.get(0).getParts()) {
                            assertThat(part.getBlockIndex(), Is.is(size));
                        }
                        assertThat(locations.size(), Is.is(1));
                        assertThat(locations.get(0).getParts().size(), Is.is(1));
                    }
                } catch (Throwable exc) {
                    failed.set(true);
                    throw new RuntimeException(exc);
                }
            }), Is.is(true));
        }
        smallBlockAssignment.flushAssignments();

        assertThat(uploadedData.size(), Is.is(0));

        assertFalse(failed.get());
    }

    @Test
    public void zipUploadExistsWrongDestination() throws InterruptedException {
        SmallFileBlockAssignment largeFileBlockAssignment = new ZipSmallBlockAssignment(uploader,
                Mockito.mock(BlockDownloader.class), repository, access, encryptionKey, 150, 300);
        expectedFormat = "ZIP";
        registerPart = true;
        set.setDestinations(Lists.newArrayList("destination", "other"));

        AtomicBoolean failed = new AtomicBoolean();
        for (int i = 1; i <= 10; i++) {
            BackupFile file = BackupFile.builder().path(i + "").length((long) i).lastChanged((long) i).build();
            assertThat(largeFileBlockAssignment.assignBlocks(set, file, (locations) -> {
            }), Is.is(true));
        }
        largeFileBlockAssignment.flushAssignments();

        assertThat(uploadedData.size(), Matchers.greaterThan(0));

        assertFalse(failed.get());
    }


    @Test
    public void encryptedUploadDownload() throws InterruptedException, IOException {
        BlockDownloader downloader = Mockito.mock(BlockDownloader.class);
        Mockito.when(downloader.downloadBlock(Mockito.any(), Mockito.eq("pwd"))).thenAnswer((t) ->
                uploadedData.get(((BackupBlock) t.getArgument(0)).getHash()));
        SmallFileBlockAssignment fileBlockAssignment = new EncryptedSmallBlockAssignment(uploader,
                downloader, repository, access, encryptionKey, 150, 300);
        expectedFormat = "ENC";

        AtomicBoolean failed = new AtomicBoolean();
        for (int i = 1; i <= 300; i++) {
            BackupFile file = BackupFile.builder().path(i + "").length((long) i).lastChanged((long) i).build();
            int size = i;
            if (size <= 150) {
                assertThat(fileBlockAssignment.assignBlocks(set, file, (locations) -> {
                    try {
                        synchronized (uploadedData) {
                            int index = 0;
                            for (BackupFilePart part : locations.get(0).getParts()) {
                                byte[] data = fileBlockAssignment.extractPart(part, null, "pwd");
                                index = data.length;

                                byte[] buffer = new byte[index];
                                for (int j = 0; j < index; j++) {
                                    buffer[j] = (byte) j;
                                }
                                assertThat(data, Is.is(buffer));
                            }
                            assertThat(index, Is.is(size));
                            assertThat(locations.size(), Is.is(1));
                            assertThat(locations.get(0).getParts().size(), Is.is(1));
                        }
                    } catch (Throwable exc) {
                        failed.set(true);
                        throw new RuntimeException(exc);
                    }
                }), Is.is(true));
            } else {
                assertThat(fileBlockAssignment.assignBlocks(set, file, (doh) -> {
                }), Is.is(false));
            }
        }
        fileBlockAssignment.flushAssignments();

        Thread.sleep(100);
        assertFalse(failed.get());
    }
}