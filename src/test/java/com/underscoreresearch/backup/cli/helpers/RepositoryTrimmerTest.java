package com.underscoreresearch.backup.cli.helpers;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.RepositoryOpenMode;
import com.underscoreresearch.backup.file.implementation.LockingMetadataRepository;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.implementation.LoggingMetadataRepository;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupLocation;
import com.underscoreresearch.backup.model.BackupManifest;
import com.underscoreresearch.backup.model.BackupRetention;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.model.BackupSetRoot;
import com.underscoreresearch.backup.model.BackupTimespan;
import org.apache.commons.lang.SystemUtils;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.underscoreresearch.backup.model.BackupTimeUnit.DAYS;
import static com.underscoreresearch.backup.model.BackupTimeUnit.HOURS;
import static com.underscoreresearch.backup.model.BackupTimeUnit.MINUTES;
import static com.underscoreresearch.backup.model.BackupTimeUnit.SECONDS;
import static com.underscoreresearch.backup.model.BackupTimeUnit.YEARS;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingInt;
import static org.hamcrest.MatcherAssert.assertThat;

class RepositoryTrimmerTest {
    private MetadataRepository repository;
    private File tempDir;
    private BackupConfiguration backupConfiguration;
    private BackupSet set1;
    private BackupSet set2;
    private BackupDestination destination;
    private ManifestManager manifestManager;
    private RepositoryTrimmer trimmer;
    private BackupFile outsideFile;
    private String root;

    @BeforeEach
    public void setup() throws IOException {
        InstanceFactory.initialize(new String[]{"--no-log", "--config-data", "{}"}, null, null);
        manifestManager = Mockito.mock(ManifestManager.class);
        tempDir = Files.createTempDirectory("test").toFile();
        repository = Mockito.spy(new LoggingMetadataRepository(new LockingMetadataRepository(tempDir.getPath(), false), manifestManager, false));
        repository.open(RepositoryOpenMode.READ_WRITE);

        root = SystemUtils.IS_OS_WINDOWS ? "C:/" : "/";

        backupConfiguration = new BackupConfiguration();
        set1 = new BackupSet();
        BackupSetRoot root1 = new BackupSetRoot();
        root1.setNormalizedPath(root + "test1");
        set1.setRoots(Lists.newArrayList(root1));
        set1.setId("set1");
        set1.setDestinations(Lists.newArrayList("mem"));
        set2 = new BackupSet();
        BackupSetRoot root2 = new BackupSetRoot();
        root2.setNormalizedPath(root + "test2");
        set2.setRoots(Lists.newArrayList(root2));
        set2.setId("set2");
        set2.setDestinations(Lists.newArrayList("mem"));
        set2.setRetention(BackupRetention.builder().defaultFrequency(new BackupTimespan(1, SECONDS))
                .retainDeleted(new BackupTimespan(1, YEARS))
                .build());
        destination = new BackupDestination();
        destination.setType("MEMORY");
        backupConfiguration.setDestinations(ImmutableMap.of("mem", destination));
        backupConfiguration.setSets(Lists.newArrayList(set1, set2));
        backupConfiguration.setManifest(new BackupManifest());

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
                .path(root + "outside")
                .length(10L)
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
                .added(Instant.now().toEpochMilli())
                .build();
        repository.addFile(outsideFile);

        addFile(root + "test1/abc/test1", 50);
        addFile(root + "test1/abc/test2", 50);
        addFile(root + "test1/def/test1", 0);
        addFile(root + "test1/def/test2", 0);
        addFile(root + "test1/def/test3", 0);
        addFile(root + "test2/test4", 0);
        addFile(root + "test1/orphaned/file", 0);

        repository.addDirectory(BackupDirectory.builder()
                .path("")
                .files(Sets.newTreeSet(Lists.newArrayList(root)))
                .added(0L)
                .build());

        repository.addDirectory(BackupDirectory.builder()
                .path(root)
                .files(Sets.newTreeSet(Lists.newArrayList("test1/", "test2/")))
                .added(new BackupTimespan(50 * 60 + 1, MINUTES).toEpochMilli())
                .build());

        repository.addDirectory(BackupDirectory.builder()
                .path(root + "test1/")
                .files(Sets.newTreeSet(Lists.newArrayList("abc/", "def/")))
                .added(new BackupTimespan(50 * 60 + 1, MINUTES).toEpochMilli())
                .build());

        repository.addDirectory(BackupDirectory.builder()
                .path(root + "test1/abc/")
                .files(Sets.newTreeSet())
                .added(new BackupTimespan(61, MINUTES).toEpochMilli())
                .build());

        repository.addDirectory(BackupDirectory.builder()
                .path(root + "test1/abc/")
                .files(Sets.newTreeSet(Lists.newArrayList("test1", "test2")))
                .added(new BackupTimespan(50 * 60 + 1, MINUTES).toEpochMilli())
                .build());

        repository.addDirectory(BackupDirectory.builder()
                .path(root + "test1/def/")
                .files(Sets.newTreeSet(Lists.newArrayList("test1", "test2")))
                .added(new BackupTimespan(61, MINUTES).toEpochMilli())
                .build());

        repository.addDirectory(BackupDirectory.builder()
                .path(root + "test1/def/")
                .files(Sets.newTreeSet(Lists.newArrayList("test1", "test2", "test3")))
                .added(new BackupTimespan(49 * 60 + 1, MINUTES).toEpochMilli())
                .build());

        repository.addDirectory(BackupDirectory.builder()
                .path(root + "test2/")
                .files(Sets.newTreeSet(Lists.newArrayList("test4")))
                .added(new BackupTimespan(49 * 60 + 1, MINUTES).toEpochMilli())
                .build());

        trimmer = new RepositoryTrimmer(repository, backupConfiguration, manifestManager, true);
    }

    private void addFile(String path, int startHours) throws IOException {
        for (int i = startHours; i < 52; i += 2) {
            repository.addFile(BackupFile
                    .builder()
                    .path(path)
                    .length((long) i)
                    .locations(Lists.newArrayList(
                            BackupLocation.builder().parts(Lists.newArrayList(
                                    BackupFilePart.builder().blockHash("e").build()
                            )).build()
                    ))
                    .added(new BackupTimespan(i * 60 + 1, MINUTES).toEpochMilli())
                    .build());
        }
    }

    @Test
    public void testFiles() throws IOException {
        trimmer.trimRepository(null);

        try (CloseableStream<BackupFile> files = repository.allFiles(true)) {
            files.stream().forEach(file -> {
                if (file.getDeleted() != null) {
                    file.setDeleted(file.getAdded());
                }
                try {
                    repository.addFile(file);
                } catch (IOException e) {
                    Assertions.fail(e);
                }
            });
        }

        trimmer.trimRepository(null);

        try (CloseableLock ignored = repository.acquireLock()) {
            Map<String, Integer> result = closeStream(repository.allFiles(true), (stream) ->
                    stream.collect(groupingBy(BackupFile::getPath, summingInt(t -> 1))));
            assertThat(result,
                    Is.is(ImmutableMap.of(root + "test1/def/test1", 13,
                            root + "test1/def/test2", 13,
                            root + "test1/def/test3", 12,
                            root + "test2/test4", 26)));

            result = closeStream(repository.allDirectories(true), stream -> stream
                    .collect(groupingBy(BackupDirectory::getPath, summingInt(t -> 1))));
            assertThat(result,
                    Is.is(ImmutableMap.of("", 1,
                            root, 1,
                            root + "test1/", 1,
                            root + "test1/def/", 1,
                            root + "test2/", 1)));

            Set<String> resultSet = closeStream(repository.allBlocks(),
                    stream -> stream.map(t -> t.getHash()).collect(Collectors.toSet()));
            assertThat(resultSet,
                    Is.is(ImmutableSet.of("e")));
        }

        IOIndex provider = (IOIndex) IOProviderFactory.getProvider(destination);
        assertThat(provider.availableKeys("/"), Is.is(Lists.newArrayList("l", "m")));
        assertThat(repository.getActivePaths(null).size(), Is.is(0));
    }

    private <T, S> S closeStream(CloseableStream<T> stream, Function<Stream<T>, S> method) throws IOException {
        try (stream) {
            return method.apply(stream.stream());
        }
    }

    @Test
    public void testMaxFiles() throws IOException {
        set1.getRetention().setMaximumVersions(1);
        set2.getRetention().setMaximumVersions(1);
        trimmer.trimRepository(null);

        closeStream(repository.allFiles(true), stream -> stream.map(file -> {
            if (file.getDeleted() != null) {
                file.setDeleted(file.getAdded());
            }
            try {
                repository.addFile(file);
            } catch (IOException e) {
                Assertions.fail(e);
            }
            return null;
        }).count());

        trimmer.trimRepository(null);

        try (CloseableLock ignored = repository.acquireLock()) {
            Map<String, Integer> result = closeStream(repository.allFiles(false), (stream) -> stream
                    .collect(groupingBy(BackupFile::getPath, summingInt(t -> 1))));
            assertThat(result,
                    Is.is(ImmutableMap.of(root + "test1/def/test1", 1,
                            root + "test1/def/test2", 1,
                            root + "test1/def/test3", 1,
                            root + "test2/test4", 1)));

            result = closeStream(repository.allDirectories(false), stream -> stream
                    .collect(groupingBy(BackupDirectory::getPath, summingInt(t -> 1))));
            assertThat(result,
                    Is.is(ImmutableMap.of("", 1,
                            root, 1,
                            root + "test1/", 1,
                            root + "test1/def/", 1,
                            root + "test2/", 1)));

            Set<String> setResult = closeStream(repository.allBlocks(),
                    stream -> stream.map(t -> t.getHash()).collect(Collectors.toSet()));
            assertThat(setResult,
                    Is.is(ImmutableSet.of("e")));
        }

        IOIndex provider = (IOIndex) IOProviderFactory.getProvider(destination);
        assertThat(provider.availableKeys("/"), Is.is(Lists.newArrayList("l", "m")));
        assertThat(repository.getActivePaths(null).size(), Is.is(0));
    }

    @Test
    public void testActivePath() throws IOException {
        repository.pushActivePath("set1", root + "test1/", new BackupActivePath());
        trimmer.trimRepository(null);

        try (CloseableLock ignored = repository.acquireLock()) {
            Map<String, Integer> result = closeStream(repository.allFiles(false), (stream) -> stream
                    .collect(groupingBy(BackupFile::getPath, summingInt(t -> 1))));
            assertThat(result,
                    Is.is(ImmutableMap.of(root + "test1/def/test1", 13,
                            root + "test1/abc/test1", 1,
                            root + "test1/abc/test2", 1,
                            root + "test1/def/test2", 13,
                            root + "test1/def/test3", 13,
                            root + "test1/orphaned/file", 13,
                            root + "test2/test4", 26)));

            result = closeStream(repository.allDirectories(false), stream -> stream
                    .collect(groupingBy(BackupDirectory::getPath, summingInt(t -> 1))));
            assertThat(result,
                    Is.is(ImmutableMap.of("", 1,
                            root, 1,
                            root + "test1/", 1,
                            root + "test1/abc/", 2,
                            root + "test1/def/", 2,
                            root + "test2/", 1)));

            Set<String> setResult = closeStream(repository.allBlocks(),
                    stream -> stream.map(BackupBlock::getHash).collect(Collectors.toSet()));
            assertThat(setResult,
                    Is.is(ImmutableSet.of("a", "b", "c", "d", "e")));
        }

        IOIndex provider = (IOIndex) IOProviderFactory.getProvider(destination);
        assertThat(provider.availableKeys("/"),
                Is.is(Lists.newArrayList("b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m")));
    }

    @Test
    public void testSpecificSet1() throws IOException {
        trimmer.trimRepository(set1);

        try (CloseableLock ignored = repository.acquireLock()) {
            Map<String, Integer> result = closeStream(repository.allFiles(false), (stream) -> stream
                    .collect(groupingBy(BackupFile::getPath, summingInt(t -> 1))));
            assertThat(result,
                    Is.is(ImmutableMap.of(root + "test1/def/test1", 13,
                            root + "test1/abc/test1", 1,
                            root + "test1/abc/test2", 1,
                            root + "test1/def/test2", 13,
                            root + "test1/def/test3", 13,
                            root + "test2/test4", 26,
                            root + "outside", 1
                    )));

            result = closeStream(repository.allDirectories(false), stream -> stream
                    .collect(groupingBy(BackupDirectory::getPath, summingInt(t -> 1))));
            assertThat(result,
                    Is.is(ImmutableMap.of("", 1,
                            root, 1,
                            root + "test1/", 1,
                            root + "test1/abc/", 1,
                            root + "test1/def/", 1,
                            root + "test2/", 1)));

            Set<String> setResult = closeStream(repository.allBlocks(),
                    stream -> stream.map(BackupBlock::getHash).collect(Collectors.toSet()));
            assertThat(setResult,
                    Is.is(ImmutableSet.of("a", "b", "c", "d", "e")));
        }

        IOIndex provider = (IOIndex) IOProviderFactory.getProvider(destination);
        assertThat(provider.availableKeys("/"),
                Is.is(Lists.newArrayList("b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m")));
    }

    @Test
    public void testSpecificSet2() throws IOException {
        trimmer.trimRepository(set2);

        try (CloseableLock ignored = repository.acquireLock()) {
            Map<String, Integer> result = closeStream(repository.allFiles(false), (stream) -> stream
                    .collect(groupingBy(BackupFile::getPath, summingInt(t -> 1))));
            assertThat(result,
                    Is.is(ImmutableMap.of(root + "test1/def/test1", 26,
                            root + "test1/abc/test1", 1,
                            root + "test1/abc/test2", 1,
                            root + "test1/def/test2", 26,
                            root + "test1/def/test3", 26,
                            root + "test1/orphaned/file", 26,
                            root + "test2/test4", 26,
                            root + "outside", 1
                    )));

            result = closeStream(repository.allDirectories(false), stream -> stream
                    .collect(groupingBy(BackupDirectory::getPath, summingInt(t -> 1))));
            assertThat(result,
                    Is.is(ImmutableMap.of("", 1,
                            root, 1,
                            root + "test1/", 1,
                            root + "test1/abc/", 2,
                            root + "test1/def/", 2,
                            root + "test2/", 1)));

            Set<String> setResult = closeStream(repository.allBlocks(),
                    stream -> stream.map(BackupBlock::getHash).collect(Collectors.toSet()));
            assertThat(setResult,
                    Is.is(ImmutableSet.of("a", "b", "c", "d", "e")));
        }

        IOIndex provider = (IOIndex) IOProviderFactory.getProvider(destination);
        assertThat(provider.availableKeys("/"),
                Is.is(Lists.newArrayList("b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m")));
    }

    @Test
    public void testOutsideNonForce() throws IOException {
        trimmer = new RepositoryTrimmer(repository, backupConfiguration, manifestManager, false);
        trimmer.trimRepository(null);

        Mockito.verify(repository, Mockito.never()).deleteFile(outsideFile);

        IOIndex provider = (IOIndex) IOProviderFactory.getProvider(destination);
        assertThat(provider.availableKeys("/"), Is.is(Lists.newArrayList("f", "g", "h", "i", "j", "k", "l", "m")));
    }

    @AfterEach
    public void teardown() throws IOException {
        repository.close();
        IOUtils.deleteContents(tempDir);
        tempDir.delete();
    }
}