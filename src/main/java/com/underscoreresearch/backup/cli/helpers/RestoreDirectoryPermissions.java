package com.underscoreresearch.backup.cli.helpers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.file.CloseableSortedMap;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MapSerializer;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.DownloadScheduler;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.underscoreresearch.backup.file.PathNormalizer.normalizedPathParent;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

@Slf4j
public class RestoreDirectoryPermissions implements Closeable {

    private final static ObjectReader READER = MAPPER.readerFor(PendingDirectory.class);
    private final static ObjectWriter WRITER = MAPPER.writerFor(PendingDirectory.class);
    private static final long CACHE_SIZE = 100;
    private static final int PERSISTED = 1;
    private static final int CHANGED = 2;

    private final LoadingCache<String, PendingDirectory> pendingDirectories;
    private final MetadataRepository repository;
    private final AtomicBoolean anyData = new AtomicBoolean(false);
    private final DownloadScheduler scheduler;
    private final FileSystemAccess fileSystemAccess;
    private final Consumer<String> callback;
    private CloseableSortedMap<String, PendingDirectory> pendingDirectoriesLarge;

    public RestoreDirectoryPermissions(MetadataRepository repository, DownloadScheduler scheduler,
                                       FileSystemAccess fileSystemAccess, boolean skipPermisssions) throws IOException {
        if (!skipPermisssions) {
            this.scheduler = scheduler;
            this.fileSystemAccess = fileSystemAccess;
            this.repository = repository;

            pendingDirectories = CacheBuilder.newBuilder()
                    .maximumSize(CACHE_SIZE)
                    .removalListener((RemovalListener<String, PendingDirectory>) notification -> {
                        if (notification.getValue().getFile() != null) {
                            if (notification.getValue().getPendingFiles().isEmpty()) {
                                if ((notification.getValue().getFlags() & PERSISTED) == PERSISTED) {
                                    getPendingDirectoriesLarge().delete(notification.getKey());
                                }
                            } else {
                                if ((notification.getValue().getFlags() & CHANGED) == CHANGED) {
                                    notification.getValue().setFlags(PERSISTED);
                                    getPendingDirectoriesLarge().put(notification.getKey(), notification.getValue());
                                }
                            }
                        }
                    })
                    .build(new CacheLoader<>() {
                        @Override
                        public PendingDirectory load(String key) throws Exception {
                            PendingDirectory directory = pendingDirectoriesLarge != null ?
                                    pendingDirectoriesLarge.get(key) : null;
                            if (directory == null) {
                                return new PendingDirectory();
                            } else {
                                return directory;
                            }
                        }
                    });

            callback = this::completeFile;

            scheduler.addCompletionCallback(callback);
        } else {
            pendingDirectories = null;
            this.scheduler = null;
            this.repository = null;
            this.fileSystemAccess = null;
            callback = null;
        }
    }

    private CloseableSortedMap<String, PendingDirectory> getPendingDirectoriesLarge() {
        if (pendingDirectoriesLarge == null) {
            try {
                pendingDirectoriesLarge = repository.temporarySortedMap(
                        new MapSerializer<>() {
                            @Override
                            public byte[] encodeKey(String s) {
                                anyData.set(true);
                                return s.getBytes(StandardCharsets.UTF_8);
                            }

                            @Override
                            public byte[] encodeValue(PendingDirectory pendingDirectory) {
                                try {
                                    return WRITER.writeValueAsBytes(pendingDirectory);
                                } catch (JsonProcessingException e) {
                                    throw new RuntimeException(e);
                                }
                            }

                            @Override
                            public PendingDirectory decodeValue(byte[] data) {
                                try {
                                    return READER.readValue(data);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }

                            @Override
                            public String decodeKey(byte[] data) {
                                return new String(data, StandardCharsets.UTF_8);
                            }
                        });
            } catch (IOException e) {
                throw new RuntimeException("Failed to keep track of directory permission restore", e);
            }
        }
        return pendingDirectoriesLarge;
    }

    @Override
    public void close() throws IOException {
        if (pendingDirectories != null) {
            scheduler.removeCompletionCallback(callback);
            pendingDirectories.invalidateAll();

            if (pendingDirectoriesLarge != null) {
                pendingDirectoriesLarge.readOnlyEntryStream(false).forEach(entry -> {
                    try {
                        fileSystemAccess.applyPermissions(new File(entry.getValue().getFile()), entry.getValue().getPermission());
                    } catch (IOException e) {
                        log.error("Failed to set permissions for path \"{}\"", entry.getKey(), e);
                    }
                });
                pendingDirectoriesLarge.close();
            }
        }
    }

    public void createDirectoryWithPermissions(File destinationFile, String path, String filePath,
                                               BackupContentsAccess contents) throws IOException {
        if (pendingDirectories != null) {
            try {
                synchronized (pendingDirectories) {
                    if (anyData.get()) {
                        PendingDirectory pendingDirectory = pendingDirectories.get(path);
                        if (pendingDirectory.getFile() != null) {
                            pendingDirectory.getPendingFiles().add(filePath);
                            if ((pendingDirectory.getFlags() & CHANGED) != CHANGED) {
                                pendingDirectory.setFlags(CHANGED | pendingDirectory.getFlags());
                            }
                            return;
                        }
                    }
                    if (!destinationFile.exists()) {
                        createDirectoryWithPermissions(destinationFile.getParentFile(), normalizedPathParent(path),
                                path, contents);
                        if (!destinationFile.mkdir())
                            throw new IOException("Failed to create path \"" + destinationFile.getAbsolutePath() + "\"");

                        String permission = contents.directoryPermissions(path);
                        if (permission != null) {
                            pendingDirectories.put(path, new PendingDirectory(permission,
                                    destinationFile.getAbsolutePath(),
                                    Lists.newArrayList(filePath), CHANGED));
                            anyData.set(true);
                        }
                    }
                }
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void completeFile(String path) {
        if (anyData.get()) {
            String parent = normalizedPathParent(path);
            synchronized (pendingDirectories) {
                try {
                    PendingDirectory directory = pendingDirectories.get(parent);
                    if (directory.getFile() == null) {
                        traverseUp(normalizedPathParent(parent));
                    } else {
                        directory.getPendingFiles().remove(path);
                        if ((directory.getFlags() & CHANGED) != CHANGED) {
                            directory.setFlags(CHANGED | directory.getFlags());
                        }
                        if (directory.getPendingFiles().isEmpty()) {
                            pendingDirectories.invalidate(parent);
                            try {
                                fileSystemAccess.applyPermissions(new File(directory.getFile()), directory.getPermission());
                            } catch (IOException e) {
                                log.error("Failed to set permissions for path \"{}\"", path, e);
                            }
                            traverseUp(parent);
                        }
                    }
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void traverseUp(String parentPath) {
        if (!parentPath.isEmpty())
            completeFile(parentPath);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class PendingDirectory {
        private String permission;
        private String file;
        private List<String> pendingFiles;
        private int flags;
    }

}
