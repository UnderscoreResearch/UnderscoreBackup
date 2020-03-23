package com.underscoreresearch.backup.manifest;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.underscoreresearch.backup.file.implementation.MapdbMetadataRepository;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.*;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.model.BackupTimeUnit.*;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingInt;
import static org.hamcrest.MatcherAssert.assertThat;

class RepositoryTrimmerTest {
    private MapdbMetadataRepository repository;
    private File tempDir;
    private BackupConfiguration backupConfiguration;
    private BackupSet set1;
    private BackupSet set2;
    private BackupDestination destination;
    private RepositoryTrimmer trimmer;
    private BackupFile outsideFile;

    @BeforeEach
    public void setup() throws IOException {
        tempDir = Files.createTempDirectory("test").toFile();
        repository = Mockito.spy(new MapdbMetadataRepository(tempDir.getPath()));
        repository.open(false);

        backupConfiguration = new BackupConfiguration();
        set1 = new BackupSet();
        set1.setNormalizedRoot("/test1");
        set1.setId("set1");
        set1.setDestinations(Lists.newArrayList("mem"));
        set2 = new BackupSet();
        set2.setNormalizedRoot("/test2");
        set2.setId("set2");
        set2.setDestinations(Lists.newArrayList("mem"));
        set2.setRetention(BackupRetention.builder().defaultFrequency(new BackupTimespan(1, SECONDS))
                .retainDeleted(new BackupTimespan(1, YEARS)).build());
        destination = new BackupDestination();
        destination.setType("MEMORY");
        backupConfiguration.setDestinations(ImmutableMap.of("mem", destination));
        backupConfiguration.setSets(Lists.newArrayList(set1, set2));

        IOProvider provider = IOProviderFactory.getProvider(destination);
        provider.upload("/b", new byte[1]);
        provider.upload("/c", new byte[1]);
        provider.upload("/d", new byte[1]);
        provider.upload("/e", new byte[1]);
        provider.upload("/f", new byte[1]);
        provider.upload("/g", new byte[1]);
        provider.upload("/h", new byte[1]);
        provider.upload("/i", new byte[1]);
        provider.upload("/j", new byte[1]);
        provider.upload("/k", new byte[1]);
        provider.upload("/l", new byte[1]);
        provider.upload("/m", new byte[1]);

        set1.setRetention(
                BackupRetention
                        .builder()
                        .retainDeleted(new BackupTimespan(2, DAYS))
                        .defaultFrequency(new BackupTimespan(3, HOURS))
                        .build());

        repository.addBlock(BackupBlock
                .builder()
                .hash("a")
                .storage(Lists.newArrayList(
                        BackupBlockStorage.builder().destination("mem").parts(Lists.newArrayList("/b", "/c")).build(),
                        BackupBlockStorage.builder().destination("mem").parts(Lists.newArrayList("/d", "/e")).build()
                ))
                .build());
        repository.addBlock(BackupBlock
                .builder()
                .hash("b")
                .storage(Lists.newArrayList(BackupBlockStorage.builder().destination("mem").parts(Lists.newArrayList("/f", "/g")).build()))
                .build());
        repository.addBlock(BackupBlock
                .builder()
                .hash("c")
                .storage(Lists.newArrayList(BackupBlockStorage.builder().destination("mem").parts(Lists.newArrayList("/h", "/i")).build()))
                .build());
        repository.addBlock(BackupBlock
                .builder()
                .hash("d")
                .storage(Lists.newArrayList(BackupBlockStorage.builder().destination("mem").parts(Lists.newArrayList("/j", "/k")).build()))
                .build());
        repository.addBlock(BackupBlock
                .builder()
                .hash("e")
                .storage(Lists.newArrayList(BackupBlockStorage.builder().destination("mem").parts(Lists.newArrayList("/l", "/m")).build()))
                .build());

        outsideFile = BackupFile
                .builder()
                .path("/outside")
                .locations(Lists.newArrayList(
                        BackupLocation.builder().parts(Lists.newArrayList(
                                BackupFilePart.builder().blockHash("b").build(),
                                BackupFilePart.builder().blockHash("c").build()
                        )).build(),
                        BackupLocation.builder().parts(Lists.newArrayList(
                                BackupFilePart.builder().blockHash("d").build(),
                                BackupFilePart.builder().blockHash("e").build()
                        )).build()
                ))
                .lastChanged(Instant.now().toEpochMilli())
                .build();
        repository.addFile(outsideFile);

        addFile("/test1/abc/test1", 50);
        addFile("/test1/abc/test2", 50);
        addFile("/test1/def/test1", 0);
        addFile("/test1/def/test2", 0);
        addFile("/test1/def/test3", 0);
        addFile("/test2/test4", 0);

        repository.addDirectory(BackupDirectory.builder()
                .path("/")
                .files(Sets.newTreeSet(Lists.newArrayList("test1/", "test2/")))
                .timestamp(new BackupTimespan(50 * 60 + 1, MINUTES).toEpochMilli())
                .build());

        repository.addDirectory(BackupDirectory.builder()
                .path("/test1/")
                .files(Sets.newTreeSet(Lists.newArrayList("abc/", "def/")))
                .timestamp(new BackupTimespan(50 * 60 + 1, MINUTES).toEpochMilli())
                .build());

        repository.addDirectory(BackupDirectory.builder()
                .path("/test1/abc/")
                .files(Sets.newTreeSet())
                .timestamp(new BackupTimespan(61, MINUTES).toEpochMilli())
                .build());

        repository.addDirectory(BackupDirectory.builder()
                .path("/test1/abc/")
                .files(Sets.newTreeSet(Lists.newArrayList("test1", "test2")))
                .timestamp(new BackupTimespan(50 * 60 + 1, MINUTES).toEpochMilli())
                .build());

        repository.addDirectory(BackupDirectory.builder()
                .path("/test1/def/")
                .files(Sets.newTreeSet(Lists.newArrayList("test1", "test2")))
                .timestamp(new BackupTimespan(61, MINUTES).toEpochMilli())
                .build());

        repository.addDirectory(BackupDirectory.builder()
                .path("/test1/def/")
                .files(Sets.newTreeSet(Lists.newArrayList("test1", "test2", "test3")))
                .timestamp(new BackupTimespan(49 * 60 + 1, MINUTES).toEpochMilli())
                .build());

        repository.addDirectory(BackupDirectory.builder()
                .path("/test2/")
                .files(Sets.newTreeSet(Lists.newArrayList("test4")))
                .timestamp(new BackupTimespan(49 * 60 + 1, MINUTES).toEpochMilli())
                .build());

        trimmer = new RepositoryTrimmer(repository, backupConfiguration, true);
    }

    private void addFile(String path, int startHours) throws IOException {
        for (int i = startHours; i < 52; i += 2) {
            repository.addFile(BackupFile
                    .builder()
                    .path(path)
                    .locations(Lists.newArrayList(
                            BackupLocation.builder().parts(Lists.newArrayList(
                                    BackupFilePart.builder().blockHash("e").build()
                            )).build()
                    ))
                    .lastChanged(new BackupTimespan(i * 60 + 1, MINUTES).toEpochMilli())
                    .build());

        }
    }

    @Test
    public void testFiles() throws IOException {
        repository.pushActivePath("set3", "/test1/", new BackupActivePath());
        trimmer.trimRepository();

        assertThat(repository.allFiles()
                        .collect(groupingBy(BackupFile::getPath, summingInt(t -> 1))),
                Is.is(ImmutableMap.of("/test1/def/test1", 13,
                        "/test1/def/test2", 13,
                        "/test1/def/test3", 12,
                        "/test2/test4", 26)));

        assertThat(repository.allDirectories()
                        .collect(groupingBy(BackupDirectory::getPath, summingInt(t -> 1))),
                Is.is(ImmutableMap.of("/", 1,
                        "/test1/", 1,
                        "/test1/def/", 2,
                        "/test2/", 1)));

        assertThat(repository.allBlocks().map(t -> t.getHash()).collect(Collectors.toSet()),
                Is.is(ImmutableSet.of("e")));

        IOIndex provider = (IOIndex) IOProviderFactory.getProvider(destination);
        assertThat(provider.availableKeys("/"), Is.is(Lists.newArrayList("l", "m")));
        assertThat(repository.getActivePaths(null).size(), Is.is(0));
    }

    @Test
    public void testActivePath() throws IOException {
        repository.pushActivePath("set1", "/test1/", new BackupActivePath());
        trimmer.trimRepository();

        assertThat(repository.allFiles()
                        .collect(groupingBy(BackupFile::getPath, summingInt(t -> 1))),
                Is.is(ImmutableMap.of("/test1/def/test1", 13,
                        "/test1/def/test2", 13,
                        "/test1/def/test3", 12,
                        "/test2/test4", 26)));

        assertThat(repository.allDirectories()
                        .collect(groupingBy(BackupDirectory::getPath, summingInt(t -> 1))),
                Is.is(ImmutableMap.of("/", 1,
                        "/test1/", 1,
                        "/test1/abc/", 2,
                        "/test1/def/", 2,
                        "/test2/", 1)));

        assertThat(repository.allBlocks().map(t -> t.getHash()).collect(Collectors.toSet()),
                Is.is(ImmutableSet.of("a", "e")));

        IOIndex provider = (IOIndex) IOProviderFactory.getProvider(destination);
        assertThat(provider.availableKeys("/"), Is.is(Lists.newArrayList("b", "c", "d", "e", "l", "m")));
    }

    @Test
    public void testOutsideNonForce() throws IOException {
        trimmer = new RepositoryTrimmer(repository, backupConfiguration, false);
        trimmer.trimRepository();

        Mockito.verify(repository, Mockito.never()).deleteFile(outsideFile);

        IOIndex provider = (IOIndex) IOProviderFactory.getProvider(destination);
        assertThat(provider.availableKeys("/"), Is.is(Lists.newArrayList("f", "g", "h", "i", "j", "k", "l", "m")));
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