package com.underscoreresearch.backup.manifest.implementation;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.manifest.implementation.BaseManifestManagerImpl.PUBLICKEY_FILENAME;
import static com.underscoreresearch.backup.manifest.implementation.ManifestManagerImpl.CONFIGURATION_FILENAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

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
import com.underscoreresearch.backup.cli.commands.BackupCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
import com.underscoreresearch.backup.encryption.encryptors.NoneEncryptor;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.RepositoryOpenMode;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.io.UploadScheduler;
import com.underscoreresearch.backup.io.implementation.UploadSchedulerImpl;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockAdditional;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupFileSpecification;
import com.underscoreresearch.backup.model.BackupManifest;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.model.BackupSetRoot;
import com.underscoreresearch.backup.model.BackupShare;

@Slf4j
class ManifestManagerImplTest {
    private static final String PUBLIC_KEY_DATA = "{\"publicKey\":\"OXYESQETTP4X4NJVUR3HTTL4OAZLVYUIFTBOEZ5ZILMJOLU4YB4A\",\"salt\":\"M7KL5D46VLT2MFXLC67KIPIPIROH2GX4NT3YJVAWOF4XN6FMMTSA\"}";

    private BackupConfiguration configuration;
    private RateLimitController rateLimitController;
    private ManifestManagerImpl manifestManager;
    private ServiceManager serviceManager;
    private Encryptor encryptor;
    private File tempDir;
    private File backupDir;
    private File shareDir;
    private EncryptionIdentity sharePrivateKey;
    private EncryptionIdentity publicKey;
    private UploadScheduler uploadScheduler;
    private IOProvider ioProvider;

    @BeforeEach
    public void setup() throws IOException, GeneralSecurityException {
        tempDir = Files.createTempDirectory("test").toFile();
        backupDir = Files.createTempDirectory("backup").toFile();
        shareDir = Files.createTempDirectory("share").toFile();
        sharePrivateKey = EncryptionIdentity.generateKeyWithPassword("testkey");
        File sourceFile = new File(System.getProperty("user.dir"), "src");

        BackupDestination shareDestination = BackupDestination.builder()
                .type("FILE")
                .endpointUri(shareDir.getAbsolutePath())
                .encryption("NONE")
                .errorCorrection("NONE")
                .build();

        serviceManager = Mockito.mock(ServiceManager.class);

        configuration = BackupConfiguration.builder()
                .destinations(ImmutableMap.of("TEST", BackupDestination.builder()
                        .type("FILE")
                        .endpointUri(backupDir.getAbsolutePath())
                        .encryption("NONE")
                        .errorCorrection("NONE")
                        .build()))
                .sets(Lists.newArrayList(BackupSet.builder()
                        .id("test")
                        .destinations(Lists.newArrayList("TEST"))
                        .roots(Lists.newArrayList(BackupSetRoot.builder()
                                .path(sourceFile.getAbsolutePath()).build()))
                        .build()))
                .shares(ImmutableMap.of(sharePrivateKey.getPrimaryKeys().getKeyIdentifier(), BackupShare.builder()
                        .contents(BackupFileSpecification.builder()
                                .roots(Lists.newArrayList(BackupSetRoot.builder()
                                        .path(new File(sourceFile, "main").getAbsolutePath()).build()))
                                .build())
                        .name("Name")
                        .destination(shareDestination)
                        .build()))
                .additionalSources(ImmutableMap.of("other", shareDestination))
                .manifest(BackupManifest.builder()
                        .destination("TEST")
                        .maximumUnsyncedSize(100)
                        .maximumUnsyncedSeconds(1)
                        .pauseOnBattery(false)
                        .build())
                .build();

        rateLimitController = Mockito.mock(RateLimitController.class);
        ioProvider = Mockito.spy(IOProviderFactory.getProvider(configuration.getDestinations().get("TEST")));
        encryptor = Mockito.spy(new NoneEncryptor());
        EncryptorFactory.injectEncryptor("NONE", encryptor);

        initializeFactory();

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            publicKey.writeKey(EncryptionIdentity.KeyFormat.UPLOAD, output);
            ioProvider.upload(PUBLICKEY_FILENAME, output.toByteArray());
        }

        IOProviderFactory.injectProvider(configuration.getDestinations().get("TEST"), ioProvider);
        uploadScheduler = new UploadSchedulerImpl(4, Mockito.mock(RateLimitController.class));
    }

    private void initializeFactory() throws JsonProcessingException {
        InstanceFactory.initialize(new String[]{"--no-log", "--password", "test", "--config-data",
                new ObjectMapper().writeValueAsString(configuration),
                "-m", tempDir.getPath(),
                "--encryption-key-data", PUBLIC_KEY_DATA}, null, null);

        publicKey = InstanceFactory.getInstance(EncryptionIdentity.class);
    }

    @Test
    public void testUploadConfig() throws IOException, GeneralSecurityException {
        Mockito.verify(encryptor, Mockito.never()).encryptBlock(any(), any(), any());
        manifestManager = new ManifestManagerImpl(configuration, tempDir.getPath(), rateLimitController, serviceManager,
                "id", null, false, publicKey, publicKey.getPrimaryKeys(),
                null, Mockito.mock(AdditionalManifestManager.class), uploadScheduler);
        manifestManager.initialize(Mockito.mock(LogConsumer.class), true);
        manifestManager.addLogEntry("doh", "doh");
        manifestManager.completeUploads();
        assertThat(ioProvider.download(CONFIGURATION_FILENAME), Matchers.not("{}".getBytes()));
    }

    @Test
    public void testUploadPending() throws IOException, GeneralSecurityException {
        File file = Paths.get(tempDir.getPath(), "logs", "2020-02-02-22-00-22.222222").toFile();
        file.getParentFile().mkdirs();
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(new byte[]{1, 2, 3});
        }
        manifestManager = new ManifestManagerImpl(configuration, tempDir.getPath(),
                rateLimitController, serviceManager, "id", null, false, publicKey,
                publicKey.getPrimaryKeys(),
                null, Mockito.mock(AdditionalManifestManager.class), uploadScheduler);
        manifestManager.initialize(Mockito.mock(LogConsumer.class), false);
        manifestManager.addLogEntry("doh", "doh");
        manifestManager.shutdown();
        Mockito.verify(encryptor, Mockito.times(3)).encryptBlock(any(), any(), any());
        assertThat(file.isFile(), Is.is(false));
        assertNotNull(ioProvider.download("logs"
                + PATH_SEPARATOR + "2020-02-02" + PATH_SEPARATOR + "22-00-22.222222.gz"));
    }

    @Test
    public void testDelayedUpload() throws IOException, InterruptedException {
        manifestManager = new ManifestManagerImpl(configuration, tempDir.getPath(),
                rateLimitController, serviceManager, "id", null, false, publicKey,
                publicKey.getPrimaryKeys(),
                null, Mockito.mock(AdditionalManifestManager.class), uploadScheduler);
        MetadataRepository firstRepository = Mockito.mock(MetadataRepository.class);
        LoggingMetadataRepository repository = new LoggingMetadataRepository(firstRepository, manifestManager, false);
        repository.deleteDirectory("/a", Instant.now().toEpochMilli());
        manifestManager.waitUploads();

        Mockito.verify(ioProvider, Mockito.times(3)).upload(anyString(), any());

        Thread.sleep(1500);

        Mockito.verify(ioProvider, Mockito.times(4)).upload(anyString(), any());
    }

    @Test
    public void testLoggingUpdateAndReplay() throws IOException, GeneralSecurityException {
        manifestManager = new ManifestManagerImpl(configuration, tempDir.getPath(),
                rateLimitController, serviceManager, "id", null, false, publicKey,
                publicKey.getPrimaryKeys(),
                null, Mockito.mock(AdditionalManifestManager.class), uploadScheduler);
        MetadataRepository firstRepository = Mockito.mock(MetadataRepository.class);
        LoggingMetadataRepository repository = new LoggingMetadataRepository(firstRepository, manifestManager, false);
        repository.upgradeStorage();
        try (CloseableLock ignored = repository.exclusiveLock()) {
            repository.deleteDirectory("/a", Instant.now().toEpochMilli());
            repository.popActivePath("s1", "/c");
            repository.addDirectory(new BackupDirectory("d", Instant.now().toEpochMilli(),
                    null,
                    Sets.newTreeSet(Lists.newArrayList("e", "f")), 1L));
            repository.deleteBlock(BackupBlock.builder().hash("g").build());
            repository.addBlock(BackupBlock.builder().hash("h").build());
            repository.deleteFilePart(BackupFilePart.builder().partHash("i").build());
            repository.addFile(BackupFile.builder().path("j").build());
            repository.deleteFile(BackupFile.builder().path("k").build());
            repository.pushActivePath("s1", "/b", new BackupActivePath());
            repository.flushLogging();
        }
        manifestManager.shutdown();

        Mockito.verify(encryptor, Mockito.atLeast(1)).encryptBlock(any(), any(), any());

        manifestManager = new ManifestManagerImpl(configuration, tempDir.getPath(),
                rateLimitController, serviceManager, "id", null, false, publicKey,
                publicKey.getPrimaryKeys(),
                null, Mockito.mock(AdditionalManifestManager.class), uploadScheduler);
        MetadataRepository secondRepository = Mockito.mock(MetadataRepository.class);
        manifestManager.replayLog(new LoggingMetadataRepository(secondRepository, manifestManager, false), "test");

        compareInvocations(firstRepository, secondRepository);
        repository.close();
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

    @Test
    public void backupWithShare() throws Exception {
        configuration.getManifest().setMaximumUnsyncedSize(1000 * 1000);
        initializeFactory();

        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
        repository.open(RepositoryOpenMode.READ_WRITE);
        manifestManager = (ManifestManagerImpl) InstanceFactory.getInstance(ManifestManager.class);
        manifestManager.activateShares(InstanceFactory.getInstance(LogConsumer.class),
                InstanceFactory.getInstance(EncryptionIdentity.class).getPrivateIdentity("test"));

        BackupCommand.executeBackup(false);

        repository.close();
    }

    @Test
    public void shareWithBackupAndDelete() throws Exception {
        configuration.getManifest().setMaximumUnsyncedSize(1000 * 1000);
        initializeFactory();

        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
        manifestManager = (ManifestManagerImpl) InstanceFactory.getInstance(ManifestManager.class);
        manifestManager.initialize(InstanceFactory.getInstance(LogConsumer.class), false);
        BackupCommand.executeBackup(false);
        manifestManager.activateShares(InstanceFactory.getInstance(LogConsumer.class),
                InstanceFactory.getInstance(EncryptionIdentity.class).getPrivateIdentity("test"));

        manifestManager.shutdown();
        InstanceFactory.shutdown();
        InstanceFactory.waitForShutdown();
        repository.close();
        configuration.setShares(new HashMap<>());
        initializeFactory();

        // Just want to make sure we have a few more blocks to delete.
        repository = InstanceFactory.getInstance(MetadataRepository.class);
        repository.addAdditionalBlock(
                BackupBlockAdditional.builder().publicKey(sharePrivateKey.getPrimaryKeys().getKeyIdentifier()).hash("a").build());
        repository.addAdditionalBlock(
                BackupBlockAdditional.builder().publicKey(sharePrivateKey.getPrimaryKeys().getKeyIdentifier()).hash("b").build());

        manifestManager = (ManifestManagerImpl) InstanceFactory.getInstance(ManifestManager.class);
        manifestManager.initialize(InstanceFactory.getInstance(LogConsumer.class), true);
        repository.close();
    }

    @AfterEach
    public void teardown() throws IOException {
        if (manifestManager != null) {
            manifestManager.shutdown();
        }
        InstanceFactory.shutdown();
        InstanceFactory.waitForShutdown();
        deleteDir(tempDir);
        deleteDir(backupDir);
        deleteDir(shareDir);
    }

    private void deleteDir(File tempDir) {
        String[] entries = tempDir.list();
        if (entries != null)
            for (String s : entries) {
                File currentFile = new File(tempDir.getPath(), s);
                if (currentFile.isDirectory()) {
                    deleteDir(currentFile);
                } else if (!currentFile.delete()) {
                    log.warn("Unable to delete {}", currentFile);
                }
            }
        if (tempDir.exists() && !tempDir.delete()) {
            log.warn("Unable to delete {}", tempDir);
        }
    }
}