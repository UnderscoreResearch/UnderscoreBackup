package com.underscoreresearch.backup.block.implementation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.UploadScheduler;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupCompletion;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupData;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupManifest;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.model.BackupSetRoot;
import com.underscoreresearch.backup.model.BackupUploadCompletion;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.underscoreresearch.backup.io.implementation.FileIOProvider.FILE_TYPE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

class FileBlockUploaderImplTest {
    static {
        try {
            InstanceFactory.initialize(new String[]{"--no-log", "--password", "test", "--config-data",
                            new ObjectMapper().writeValueAsString(createConfiguration()), "--encryption-key-data",
                            "{\"publicKey\":\"OXYESQETTP4X4NJVUR3HTTL4OAZLVYUIFTBOEZ5ZILMJOLU4YB4A\",\"salt\":\"M7KL5D46VLT2MFXLC67KIPIPIROH2GX4NT3YJVAWOF4XN6FMMTSA\"}"},
                    null, null);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private MetadataRepository repository;
    private UploadScheduler scheduler;
    private BackupConfiguration configuration;
    private FileBlockUploaderImpl fileBlockUploader;
    private ManifestManager manifestManager;
    private BackupDestination destination1;
    private BackupDestination destination2;
    private BackupSet set;
    private boolean delay;

    private static BackupConfiguration createConfiguration() {
        BackupSet set = new BackupSet();
        set.setId("id");
        set.setRoots(Lists.newArrayList(BackupSetRoot.builder().path("/").build()));
        set.setDestinations(Lists.newArrayList("dest2"));

        return BackupConfiguration.builder()
                .sets(Lists.newArrayList(set))
                .destinations(ImmutableMap.of(
                        "dest1", BackupDestination.builder().encryption("AES256")
                                .endpointUri(new File(".").getAbsolutePath()).type(FILE_TYPE).errorCorrection("RS").build(),
                        "dest2", BackupDestination.builder().encryption("NONE")
                                .endpointUri(new File(".").getAbsolutePath()).type(FILE_TYPE).errorCorrection("NONE").build()))
                .manifest(BackupManifest.builder().destination("dest2").build())
                .build();
    }

    @BeforeEach
    public void setup() throws GeneralSecurityException {
        repository = Mockito.mock(MetadataRepository.class);
        scheduler = Mockito.mock(UploadScheduler.class);

        delay = true;
        Mockito.doAnswer((t) -> {
            String key = t.getArgument(1);
            Integer index = t.getArgument(2);
            BackupUploadCompletion completion = t.getArgument(5);

            String doneKey;
            if (index != null)
                doneKey = key + "-" + index;
            else
                doneKey = key;

            if (delay) {
                Thread thread = new Thread(() -> {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                    }
                    completion.completed(doneKey);
                });
                thread.setDaemon(true);

                thread.start();
            } else {
                completion.completed(doneKey);
            }

            return null;
        }).when(scheduler).scheduleUpload(any(), any(), anyInt(), anyInt(), any(), any());

        configuration = createConfiguration();
        destination1 = configuration.getDestinations().get("dest1");
        destination2 = configuration.getDestinations().get("dest2");
        set = configuration.getSets().get(0);

        manifestManager = Mockito.mock(ManifestManager.class);
        Mockito.when(manifestManager.getActivatedShares()).thenReturn(new HashMap<>());

        fileBlockUploader = new FileBlockUploaderImpl(configuration, repository, scheduler, manifestManager,
                EncryptionIdentity.generateKeyWithPassword("doh"));
    }

    @Test
    public void basic() throws IOException {
        AtomicBoolean success = new AtomicBoolean();
        synchronized (success) {
            BackupCompletion completion = new BackupCompletion() {
                @Override
                public void completed(boolean val) {
                    synchronized (success) {
                        success.set(val);
                        success.notify();
                    }
                }
            };
            fileBlockUploader.uploadBlock(set, new BackupData(new byte[100]), "hash", "RAW", completion);
            if (!success.get()) {
                try {
                    success.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        assertThat(success.get(), Is.is(true));
        Mockito.verify(scheduler).scheduleUpload(eq(destination2), eq("hash"), eq(0), eq(0), eq(new byte[100]), any());
        ArgumentCaptor<BackupBlock> block = ArgumentCaptor.forClass(BackupBlock.class);
        Mockito.verify(repository).addBlock(block.capture());

        assertThat(block.getValue(), Is.is(BackupBlock.builder()
                .hash("hash")
                .created(block.getValue().getCreated())
                .storage(Lists.newArrayList(BackupBlockStorage.builder()
                        .destination("dest2")
                        .encryption("NONE")
                        .ec("NONE")
                        .parts(Lists.newArrayList("hash-0"))
                        .build()))
                .format("RAW")
                .build()));
    }

    @Test
    public void encryptionAndErrorCorrection() throws IOException {
        delay = false;
        set.setDestinations(Lists.newArrayList("dest1"));
        AtomicBoolean success = new AtomicBoolean();
        synchronized (success) {
            BackupCompletion completion = new BackupCompletion() {
                @Override
                public void completed(boolean val) {
                    synchronized (success) {
                        success.set(val);
                        success.notify();
                    }
                }
            };
            fileBlockUploader.uploadBlock(set, new BackupData(new byte[100]), "hash", "RAW", completion);
            if (!success.get()) {
                try {
                    success.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        assertThat(success.get(), Is.is(true));
        Mockito.verify(scheduler, Mockito.times(20)).scheduleUpload(eq(destination1), eq("hash"), anyInt(), anyInt(), any(), any());
        Mockito.verify(repository).addBlock(any());
    }

    @Test
    public void existingBlockAlreadyDone() throws IOException {
        Mockito.when(repository.block("hash")).thenReturn(BackupBlock.builder()
                .format("RAW")
                .storage(Lists.newArrayList(BackupBlockStorage.builder()
                                .destination("dest1")
                                .build(),
                        BackupBlockStorage.builder()
                                .destination("dest2")
                                .build())).build());

        AtomicBoolean success = new AtomicBoolean();
        synchronized (success) {
            BackupCompletion completion = new BackupCompletion() {
                @Override
                public void completed(boolean val) {
                    synchronized (success) {
                        success.set(val);
                        success.notify();
                    }
                }
            };
            fileBlockUploader.uploadBlock(set, new BackupData(new byte[100]), "hash", "RAW", completion);
            if (!success.get()) {
                try {
                    success.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        assertThat(success.get(), Is.is(true));
        Mockito.verify(repository, Mockito.never()).addBlock(any());
        Mockito.verify(scheduler, Mockito.never()).scheduleUpload(any(), any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    public void existingBlockWrongType() throws IOException {
        Mockito.when(repository.block("hash")).thenReturn(BackupBlock.builder()
                .format("GZIP")
                .storage(Lists.newArrayList(BackupBlockStorage.builder()
                                .destination("dest1")
                                .build(),
                        BackupBlockStorage.builder()
                                .destination("dest2")
                                .build())).build());

        AtomicBoolean success = new AtomicBoolean();
        synchronized (success) {
            BackupCompletion completion = new BackupCompletion() {
                @Override
                public void completed(boolean val) {
                    synchronized (success) {
                        success.set(val);
                        success.notify();
                    }
                }
            };
            fileBlockUploader.uploadBlock(set, new BackupData(new byte[100]), "hash", "RAW", completion);
            if (!success.get()) {
                try {
                    success.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        assertThat(success.get(), Is.is(true));
        Mockito.verify(repository, Mockito.never()).addBlock(any());
        Mockito.verify(scheduler, Mockito.never()).scheduleUpload(any(), any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    public void existingBlockPartialRightType() throws IOException {
        Mockito.when(repository.block("hash")).thenReturn(BackupBlock.builder()
                .format("RAW")
                .storage(Lists.newArrayList(BackupBlockStorage.builder()
                        .destination("dest1")
                        .build()))
                .build());
        set.setDestinations(Lists.newArrayList("dest1", "dest2"));

        AtomicBoolean success = new AtomicBoolean();
        synchronized (success) {
            BackupCompletion completion = new BackupCompletion() {
                @Override
                public void completed(boolean val) {
                    synchronized (success) {
                        success.set(val);
                        success.notify();
                    }
                }
            };
            fileBlockUploader.uploadBlock(set, new BackupData(new byte[100]), "hash", "RAW", completion);
            if (!success.get()) {
                try {
                    success.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        assertThat(success.get(), Is.is(true));
        Mockito.verify(repository).addBlock(any());
        Mockito.verify(scheduler).scheduleUpload(any(), any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    public void existingBlockPartialWrongType() throws IOException, GeneralSecurityException {
        configuration.getSets().add(BackupSet.builder().destinations(Lists.newArrayList("dest1")).build());
        fileBlockUploader = new FileBlockUploaderImpl(configuration, repository, scheduler, manifestManager,
                EncryptionIdentity.generateKeyWithPassword("doh"));

        Mockito.when(repository.block("hash")).thenReturn(BackupBlock.builder()
                .format("GZIP")
                .storage(Lists.newArrayList(BackupBlockStorage.builder()
                        .destination("dest1")
                        .build()))
                .build());
        set.setDestinations(Lists.newArrayList("dest2"));

        AtomicBoolean success = new AtomicBoolean();
        synchronized (success) {
            BackupCompletion completion = new BackupCompletion() {
                @Override
                public void completed(boolean val) {
                    synchronized (success) {
                        success.set(val);
                        success.notify();
                    }
                }
            };
            fileBlockUploader.uploadBlock(set, new BackupData(new byte[100]), "hash", "RAW", completion);
            if (!success.get()) {
                try {
                    success.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        assertThat(success.get(), Is.is(true));
        Mockito.verify(repository).addBlock(any());
        Mockito.verify(scheduler, Mockito.times(21)).scheduleUpload(any(), any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    public void existingBlockPartialWrongTypeUnused() throws IOException {
        Mockito.when(repository.block("hash")).thenReturn(BackupBlock.builder()
                .format("GZIP")
                .storage(Lists.newArrayList(BackupBlockStorage.builder()
                        .destination("dest1")
                        .build()))
                .build());
        set.setDestinations(Lists.newArrayList("dest2"));

        AtomicBoolean success = new AtomicBoolean();
        synchronized (success) {
            BackupCompletion completion = new BackupCompletion() {
                @Override
                public void completed(boolean val) {
                    synchronized (success) {
                        success.set(val);
                        success.notify();
                    }
                }
            };
            fileBlockUploader.uploadBlock(set, new BackupData(new byte[100]), "hash", "RAW", completion);
            if (!success.get()) {
                try {
                    success.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        assertThat(success.get(), Is.is(true));
        Mockito.verify(repository).addBlock(any());
        Mockito.verify(scheduler, Mockito.times(1)).scheduleUpload(any(), any(), anyInt(), anyInt(), any(), any());
    }
}