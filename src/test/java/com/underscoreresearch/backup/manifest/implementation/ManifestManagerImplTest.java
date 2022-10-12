package com.underscoreresearch.backup.manifest.implementation;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.hamcrest.Matchers;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.encryption.NoneEncryptor;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.implementation.MemoryIOProvider;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.LoggingMetadataRepository;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupManifest;

class ManifestManagerImplTest {
    private static final String PUBLIC_KEY_DATA = "{\"publicKey\":\"OXYESQETTP4X4NJVUR3HTTL4OAZLVYUIFTBOEZ5ZILMJOLU4YB4A\",\"salt\":\"M7KL5D46VLT2MFXLC67KIPIPIROH2GX4NT3YJVAWOF4XN6FMMTSA\"}";

    private BackupConfiguration configuration;
    private RateLimitController rateLimitController;
    private ManifestManagerImpl manifestManager;
    private MemoryIOProvider memoryIOProvider;
    private Encryptor encryptor;
    private File tempDir;

    @BeforeEach
    public void setup() throws IOException {
        tempDir = Files.createTempDirectory("test").toFile();

        configuration = BackupConfiguration.builder()
                .destinations(ImmutableMap.of("TEST", BackupDestination.builder()
                        .type("FILE")
                        .endpointUri("file:///test/")
                        .encryption("AES256")
                        .build()))
                .manifest(BackupManifest.builder()
                        .destination("TEST")
                        .maximumUnsyncedSize(100)
                        .maximumUnsyncedSeconds(1)
                        .localLocation(tempDir.getPath())
                        .build())
                .build();

        String configurationData = new ObjectMapper().writeValueAsString(configuration);

        InstanceFactory.initialize(new String[]{"--no-log", "--passphrase", "test", "--config-data", configurationData,
                "--public-key-data", PUBLIC_KEY_DATA}, null, null);

        rateLimitController = Mockito.mock(RateLimitController.class);
        memoryIOProvider = Mockito.spy(new MemoryIOProvider(null));
        memoryIOProvider.upload("publickey.json", PUBLIC_KEY_DATA.getBytes("UTF-8"));
        encryptor = Mockito.spy(new NoneEncryptor());
    }

    @Test
    public void testUploadConfig() throws IOException {
        Mockito.verify(encryptor, Mockito.never()).encryptBlock(any(), any());
        manifestManager = new ManifestManagerImpl(configuration, tempDir.getPath(), memoryIOProvider, encryptor, rateLimitController, "id", null, false);
        manifestManager.addLogEntry("doh", "doh");
        assertThat(memoryIOProvider.download("configuration.json"), Matchers.not("{}".getBytes()));
    }

    @Test
    public void testUploadPending() throws IOException {
        File file = Paths.get(tempDir.getPath(), "logs", "2020-02-02-22-00-22.222222").toFile();
        file.getParentFile().mkdirs();
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(new byte[]{1, 2, 3});
        }
        manifestManager = new ManifestManagerImpl(configuration, tempDir.getPath(), memoryIOProvider, encryptor, rateLimitController, "id", null, false);
        manifestManager.initialize(Mockito.mock(LogConsumer.class), false);
        manifestManager.addLogEntry("doh", "doh");
        Mockito.verify(encryptor, Mockito.times(2)).encryptBlock(any(), any());
        assertThat(file.isFile(), Is.is(false));
        assertNotNull(memoryIOProvider.download("logs"
                + PATH_SEPARATOR + "2020-02-02" + PATH_SEPARATOR + "22-00-22.222222.gz"));
    }

    @Test
    public void testDelayedUpload() throws IOException, InterruptedException {
        manifestManager = new ManifestManagerImpl(configuration, tempDir.getPath(), memoryIOProvider, encryptor, rateLimitController, "id", null, false);
        MetadataRepository firstRepository = Mockito.mock(MetadataRepository.class);
        LoggingMetadataRepository repository = new LoggingMetadataRepository(firstRepository, manifestManager, false);
        repository.deleteDirectory("/a", Instant.now().toEpochMilli());

        Mockito.verify(memoryIOProvider, Mockito.times(3)).upload(anyString(), any());

        Thread.sleep(1500);

        Mockito.verify(memoryIOProvider, Mockito.times(4)).upload(anyString(), any());
    }

    @Test
    public void testLoggingUpdateAndReplay() throws IOException {
        manifestManager = new ManifestManagerImpl(configuration, tempDir.getPath(), memoryIOProvider, encryptor, rateLimitController, "id", null, false);
        MetadataRepository firstRepository = Mockito.mock(MetadataRepository.class);
        LoggingMetadataRepository repository = new LoggingMetadataRepository(firstRepository, manifestManager, false);
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

        Mockito.verify(encryptor, Mockito.atLeast(1)).encryptBlock(any(), any());

        manifestManager = new ManifestManagerImpl(configuration, tempDir.getPath(), memoryIOProvider, encryptor, rateLimitController, "id", null, false);
        MetadataRepository secondRepository = Mockito.mock(MetadataRepository.class);
        manifestManager.replayLog(new LoggingMetadataRepository(secondRepository, manifestManager, false));

        compareInvocations(firstRepository, secondRepository);
    }

    private void compareInvocations(MetadataRepository repository1, MetadataRepository repository2) throws JsonProcessingException {
        List<Invocation> det1 = stripInvocations(repository1);
        List<Invocation> det2 = stripInvocations(repository2);
        assertThat(createInvocationString(det1), Is.is(createInvocationString(det2)));
    }

    private String createInvocationString(List<Invocation> det) throws JsonProcessingException {
        StringBuilder builder = new StringBuilder();
        for (Invocation inv : det) {
            builder.append(inv.getMethod().getName());
            builder.append("(");
            ObjectMapper mapper = new ObjectMapper();
            for (int i = 0; i < inv.getRawArguments().length; i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(mapper.writeValueAsString(inv.getRawArguments()[i]));
            }
            builder.append(")");
            builder.append("\n");
        }
        return builder.toString();
    }

    private List<Invocation> stripInvocations(MetadataRepository repository1) {
        return Mockito.mockingDetails(repository1).getInvocations().stream()
                .filter(t -> {
                    switch (t.getMethod().getName()) {
                        default:
                            return true;
                        case "directory":
                        case "lastDirectory":
                        case "hasActivePath":
                        case "flushLogging":
                        case "lastSyncedLogFile":
                        case "setLastSyncedLogFile":
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