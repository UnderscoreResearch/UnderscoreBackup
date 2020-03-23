package com.underscoreresearch.backup.manifest.implementation;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.encryption.NoneEncryptor;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.implementation.MemoryIOProvider;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.manifest.LoggingMetadataRepository;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.*;
import org.hamcrest.core.Is;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

class ManifestManagerImplTest {
    private static final String PUBLIC_KEY_DATA = "{\"publicKey\":\"OXYESQETTP4X4NJVUR3HTTL4OAZLVYUIFTBOEZ5ZILMJOLU4YB4A\",\"salt\":\"M7KL5D46VLT2MFXLC67KIPIPIROH2GX4NT3YJVAWOF4XN6FMMTSA\"}";
    ;
    private BackupConfiguration configuration;
    private RateLimitController rateLimitController;
    private ManifestManagerImpl manifestManager;
    private MemoryIOProvider memoryIOProvider;
    private Encryptor encryptor;
    private File tempDir;

    @BeforeEach
    public void setup() throws IOException {
        InstanceFactory.initialize(new String[]{"--private-key-seed", "test", "--config-data", "{}", "--public-key-data",
                PUBLIC_KEY_DATA});

        tempDir = Files.createTempDirectory("test").toFile();

        configuration = BackupConfiguration.builder()
                .destinations(ImmutableMap.of("TEST", BackupDestination.builder()
                        .encryption("AES256")
                        .build()))
                .manifest(BackupManifest.builder()
                        .destination("TEST")
                        .maximumUnsyncedSize(100)
                        .maximumUnsyncedSeconds(1)
                        .localLocation(tempDir.getPath())
                        .build())
                .build();

        rateLimitController = Mockito.mock(RateLimitController.class);
        memoryIOProvider = Mockito.spy(new MemoryIOProvider(null));
        memoryIOProvider.upload("publickey.json", PUBLIC_KEY_DATA.getBytes("UTF-8"));
        encryptor = Mockito.spy(new NoneEncryptor());
    }

    @Test
    public void testUploadConfig() throws IOException {
        Mockito.verify(encryptor, Mockito.never()).encryptBlock(any());
        manifestManager = new ManifestManagerImpl(configuration, memoryIOProvider, encryptor, rateLimitController);
        manifestManager.addLogEntry("doh", "doh");
        assertThat(memoryIOProvider.download("configuration.json"), Is.is("{}".getBytes()));
    }

    @Test
    public void testUploadPending() throws IOException {
        File file = Paths.get(tempDir.getPath(), "logs", "2020-02-02-22-00-22.222222.gz").toFile();
        file.getParentFile().mkdirs();
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(new byte[]{1, 2, 3});
        }
        manifestManager = new ManifestManagerImpl(configuration, memoryIOProvider, encryptor, rateLimitController);
        manifestManager.addLogEntry("doh", "doh");
        Mockito.verify(encryptor).encryptBlock(any());
        assertThat(file.isFile(), Is.is(false));
        assertNotNull(memoryIOProvider.download("logs"
                + PATH_SEPARATOR + "2020-02-02" + PATH_SEPARATOR + "22-00-22.222222.gz"));
    }

    @Test
    public void testDelayedUpload() throws IOException, InterruptedException {
        manifestManager = new ManifestManagerImpl(configuration, memoryIOProvider, encryptor, rateLimitController);
        MetadataRepository firstRepository = Mockito.mock(MetadataRepository.class);
        LoggingMetadataRepository repository = new LoggingMetadataRepository(firstRepository, manifestManager);
        repository.deleteDirectory("/a", Instant.now().toEpochMilli());

        Mockito.verify(memoryIOProvider, Mockito.times(2)).upload(anyString(), any());

        Thread.sleep(1500);

        Mockito.verify(memoryIOProvider, Mockito.times(3)).upload(anyString(), any());
    }

    @Test
    public void testLoggingUpdateAndReplay() throws IOException {
        manifestManager = new ManifestManagerImpl(configuration, memoryIOProvider, encryptor, rateLimitController);
        MetadataRepository firstRepository = Mockito.mock(MetadataRepository.class);
        LoggingMetadataRepository repository = new LoggingMetadataRepository(firstRepository, manifestManager);
        repository.deleteDirectory("/a", Instant.now().toEpochMilli());
        repository.popActivePath("s1", "/c");
        repository.addDirectory(new BackupDirectory("d", Instant.now().toEpochMilli(),
                Sets.newTreeSet(Lists.newArrayList("e", "f"))));
        repository.deleteBlock(BackupBlock.builder().hash("g").build());
        repository.addBlock(BackupBlock.builder().hash("h").build());
        repository.deleteFilePart(BackupFilePart.builder().partHash("i").build());
        repository.addFile(BackupFile.builder().path("j").build());
        repository.deleteFile(BackupFile.builder().path("k").build());
        repository.pushActivePath("s1", "/b", new BackupActivePath());
        repository.flushLogging();
        manifestManager.shutdown();

        Mockito.verify(encryptor, Mockito.atLeast(1)).encryptBlock(any());

        manifestManager = new ManifestManagerImpl(configuration, memoryIOProvider, encryptor, rateLimitController);
        MetadataRepository secondRepository = Mockito.mock(MetadataRepository.class);
        manifestManager.replayLog(new LoggingMetadataRepository(secondRepository, manifestManager));

        compareInvocations(firstRepository, secondRepository);
    }

    private void compareInvocations(MetadataRepository repository1, MetadataRepository repository2) {
        List<Invocation> det1 = stripInvications(repository1);
        List<Invocation> det2 = stripInvications(repository2);
        assertThat(det1.size(), Is.is(det2.size()));
        for (int i = 0; i < det1.size(); i++) {
            assertThat(det1.get(i).getMethod(), Is.is(det2.get(i).getMethod()));
            assertThat(Lists.newArrayList(det1.get(i).getRawArguments()),
                    Is.is(Lists.newArrayList(det2.get(i).getRawArguments())));
        }
    }

    @NotNull
    private List<Invocation> stripInvications(MetadataRepository repository1) {
        return Mockito.mockingDetails(repository1).getInvocations().stream()
                .filter(t -> {
                    switch (t.getMethod().getName()) {
                        default:
                            return true;
                        case "directory":
                        case "lastDirectory":
                        case "hasActivePath":
                        case "flushLogging":
                            return false;
                    }
                })
                .collect(Collectors.toList());
    }

    @AfterEach
    public void teardown() {
        deleteDir(tempDir);
    }

    private void deleteDir(File tempDir) {
        String[] entries = tempDir.list();
        for (String s : entries) {
            File currentFile = new File(tempDir.getPath(), s);
            if (currentFile.isDirectory()) {
                deleteDir(currentFile);
            }
            currentFile.delete();
        }
        tempDir.delete();
    }
}