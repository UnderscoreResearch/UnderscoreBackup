package com.underscoreresearch.backup.manifest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.manifest.model.PushActivePath;
import com.underscoreresearch.backup.model.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;

@Slf4j
public class LoggingMetadataRepository implements MetadataRepository, LogConsumer {
    private static final long CURRENT_SPAN = 60 * 1000;
    private final MetadataRepository repository;
    private final ManifestManager manifestManager;
    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, LogReader> decoders;
    private final Map<String, PendingActivePath> pendingActivePaths = new HashMap<>();
    private final Set<String> missingActivePaths = new HashSet<>();
    private final int activePathDelay;
    private static ScheduledThreadPoolExecutor activePathSubmittors = new ScheduledThreadPoolExecutor(1);

    @Data
    private static class PendingActivePath {
        private BackupActivePath path;
        private Instant submitted;

        public PendingActivePath(BackupActivePath path) {
            this.path = path;
            submitted = Instant.now();
        }
    }

    public LoggingMetadataRepository(MetadataRepository repository,
                                     ManifestManager manifestManager) {
        this(repository, manifestManager, 60 * 1000);
    }

    public LoggingMetadataRepository(MetadataRepository repository,
                                     ManifestManager manifestManager,
                                     int activePathDelay) {
        this.repository = repository;
        this.manifestManager = manifestManager;
        this.activePathDelay = activePathDelay;

        activePathSubmittors.scheduleAtFixedRate(() -> submitPendingActivePaths(Duration.ofMillis(activePathDelay)),
                Math.min(activePathDelay, 1000), Math.min(activePathDelay, 1000), TimeUnit.MILLISECONDS);

        decoders = ImmutableMap.<String, LogReader>builder()
                .put("file", (json) -> repository.addFile(mapper.readValue(json, BackupFile.class)))
                .put("deleteFile", (json) -> repository.deleteFile(mapper.readValue(json, BackupFile.class)))
                .put("deletePart", (json) -> repository.deleteFilePart(mapper.readValue(json, BackupFilePart.class)))
                .put("block", (json) -> repository.addBlock(mapper.readValue(json, BackupBlock.class)))
                .put("deleteBlock", (json) -> repository.deleteBlock(mapper.readValue(json, BackupBlock.class)))
                .put("dir", (json) -> {
                    BackupDirectory dir = mapper.readValue(json, BackupDirectory.class);
                    repository.addDirectory(dir);
                })
                .put("deleteDir", (json) -> {
                    BackupDirectory dir = mapper.readValue(json, BackupDirectory.class);
                    repository.deleteDirectory(dir.getPath(), dir.getTimestamp());
                })
                .put("path", (json) -> {
                    PushActivePath activePath = mapper.readValue(json, PushActivePath.class);
                    repository.pushActivePath(activePath.getSetId(), activePath.getPath(), activePath.getActivePath());
                })
                .put("deletePath", (json) -> {
                    PushActivePath activePath = mapper.readValue(json, PushActivePath.class);
                    repository.popActivePath(activePath.getSetId(), activePath.getPath());
                })
                .build();
    }

    private void submitPendingActivePaths(Duration age) {
        Instant expired = Instant.now().minus(age);

        synchronized (pendingActivePaths) {
            Iterator<Map.Entry<String, PendingActivePath>> iterator = pendingActivePaths.entrySet().iterator();
            while (iterator.hasNext()) {
                try {
                    Map.Entry<String, PendingActivePath> entry = iterator.next();
                    if (!entry.getValue().getSubmitted().isAfter(expired)) {
                        int ind = entry.getKey().indexOf(PATH_SEPARATOR);
                        String setId = entry.getKey().substring(0, ind);
                        String path = entry.getKey().substring(ind + 1);

                        repository.pushActivePath(setId, path, entry.getValue().getPath());
                        writeLogEntry("path", new PushActivePath(setId, path, entry.getValue().getPath()));
                        missingActivePaths.remove(entry.getKey());
                        iterator.remove();
                    }
                } catch (IOException e) {
                    log.error("Failed to serialzie pending files", e);
                }
            }
        }
    }

    @Override
    public void replayLogEntry(String type, String jsonDefinition) throws IOException {
        decoders.get(type).applyJson(jsonDefinition);
    }

    private interface LogReader {
        void applyJson(String json) throws IOException;
    }

    private synchronized void writeLogEntry(String type, Object obj) {
        try {
            manifestManager.addLogEntry(type, mapper.writeValueAsString(obj));
        } catch (JsonProcessingException e) {
            log.error("Failed to process " + type, e);
        }
    }

    @Override
    public void addFile(BackupFile file) throws IOException {
        writeLogEntry("file", file);
        repository.addFile(file);
    }

    @Override
    public List<BackupFile> file(String path) throws IOException {
        return repository.file(path);
    }

    @Override
    public BackupFile lastFile(String path) throws IOException {
        return repository.lastFile(path);
    }

    @Override
    public boolean deleteFile(BackupFile file) throws IOException {
        writeLogEntry("deleteFile", file);
        return repository.deleteFile(file);
    }

    @Override
    public List<BackupFilePart> existingFilePart(String partHash) throws IOException {
        return repository.existingFilePart(partHash);
    }

    @Override
    public Stream<BackupFile> allFiles() throws IOException {
        return repository.allFiles();
    }

    @Override
    public Stream<BackupBlock> allBlocks() throws IOException {
        return repository.allBlocks();
    }

    @Override
    public Stream<BackupDirectory> allDirectories() throws IOException {
        return repository.allDirectories();
    }

    @Override
    public boolean deleteFilePart(BackupFilePart filePart) throws IOException {
        writeLogEntry("deletePart", filePart);
        return repository.deleteFilePart(filePart);
    }

    @Override
    public void addBlock(BackupBlock block) throws IOException {
        writeLogEntry("block", block);
        repository.addBlock(block);
    }

    @Override
    public BackupBlock block(String hash) throws IOException {
        return repository.block(hash);
    }

    @Override
    public boolean deleteBlock(BackupBlock block) throws IOException {
        writeLogEntry("deleteBlock", block);
        return repository.deleteBlock(block);
    }

    @Override
    public void addDirectory(BackupDirectory directory) throws IOException {
        BackupDirectory currentData;
        if (Instant.now().toEpochMilli() - directory.getTimestamp() < CURRENT_SPAN)
            currentData = repository.lastDirectory(directory.getPath());
        else {
            List<BackupDirectory> set = repository.directory(directory.getPath());

            currentData = null;
            if (set != null) {
                for (BackupDirectory entry : set) {
                    if (entry.getTimestamp() <= directory.getTimestamp()) {
                        currentData = entry;
                    } else {
                        break;
                    }
                }
            }
        }

        if (currentData == null || !directory.getFiles().equals(currentData.getFiles())) {
            writeLogEntry("dir", directory);
            repository.addDirectory(directory);
        }
    }

    @Override
    public List<BackupDirectory> directory(String path) throws IOException {
        return repository.directory(path);
    }

    @Override
    public BackupDirectory lastDirectory(String path) throws IOException {
        return repository.lastDirectory(path);
    }

    @Override
    public boolean deleteDirectory(String path, long timestamp) throws IOException {
        writeLogEntry("deleteDir", new BackupDirectory(path, timestamp, null));
        return repository.deleteDirectory(path, timestamp);
    }

    @Override
    public void pushActivePath(String setId, String path, BackupActivePath pendingFiles) throws IOException {
        synchronized (pendingActivePaths) {
            String fullPath = setId + PATH_SEPARATOR + path;

            if (!pendingActivePaths.containsKey(fullPath) && !repository.hasActivePath(setId, path))
                missingActivePaths.add(fullPath);
            pendingActivePaths.put(fullPath, new PendingActivePath(new BackupActivePath(path,
                    pendingFiles
                            .getFiles()
                            .stream()
                            .map((file) -> new BackupActiveFile(file.getPath(), file.getStatus()))
                            .collect(Collectors.toSet()))));
        }
    }

    @Override
    public boolean hasActivePath(String setId, String path) throws IOException {
        return repository.hasActivePath(setId, path);
    }

    @Override
    public void popActivePath(String setId, String path) throws IOException {
        String fullPath = setId + PATH_SEPARATOR + path;
        synchronized (pendingActivePaths) {
            pendingActivePaths.remove(fullPath);
            if (!missingActivePaths.remove(fullPath)) {
                repository.popActivePath(setId, path);
                writeLogEntry("deletePath", new PushActivePath(setId, path, null));
            }
        }

    }

    @Override
    public TreeMap<String, BackupActivePath> getActivePaths(String setId) throws IOException {
        flushLogging();
        return repository.getActivePaths(setId);
    }

    @Override
    public void flushLogging() throws IOException {
        synchronized (pendingActivePaths) {
            submitPendingActivePaths(Duration.ofMillis(0));
            pendingActivePaths.clear();
            missingActivePaths.clear();
        }

        repository.flushLogging();
    }

    @Override
    public void open(boolean readOnly) throws IOException {
        repository.open(readOnly);
    }

    @Override
    public void close() throws IOException {
        flushLogging();
        repository.close();
    }
}
