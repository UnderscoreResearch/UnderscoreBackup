package com.underscoreresearch.backup.manifest.implementation;

import static com.underscoreresearch.backup.file.PathNormalizer.ROOT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.manifest.BackupSearchAccess;
import com.underscoreresearch.backup.model.BackupFile;

@RequiredArgsConstructor
public class BackupSearchAccessImpl implements BackupSearchAccess {
    private final MetadataRepository repository;
    private final BackupContentsAccess contentsAccess;
    private final Long timestamp;
    private final boolean includeDeleted;
    private final LoadingCache<String, Set<String>> directoryCache = CacheBuilder
            .newBuilder()
            .maximumSize(50)
            .build(new CacheLoader<>() {
                @Override
                public Set<String> load(String key) throws Exception {
                    List<BackupFile> directory = contentsAccess.directoryFiles(key);
                    if (directory == null) {
                        return new HashSet<>();
                    }
                    return directory.stream()
                            .map(BackupFile::getPath).collect(Collectors.toSet());
                }
            });

    @Override
    public CloseableLock acquireLock() {
        return repository.acquireLock();
    }

    @Override
    public CloseableStream<BackupFile> searchFiles(Pattern pathPattern, CloseableLock interruptableLock) throws IOException {
        AtomicReference<List<BackupFile>> filesPerPath = new AtomicReference<>(new ArrayList<>());
        AtomicReference<String> currentPath = new AtomicReference<>();
        CloseableStream<BackupFile> closeableStream = repository.allFiles(true);
        Stream<BackupFile> stream = closeableStream.stream().map(file -> {
                    if (interruptableLock.requested()) {
                        throw new InterruptedSearch();
                    }
                    if (file.getPath().equals(currentPath.get())) {
                        if (filesPerPath.get() != null) {
                            filesPerPath.get().add(file);
                        }
                    } else {
                        List<BackupFile> ret = filesPerPath.get();
                        if (pathPattern.matcher(PathNormalizer.physicalPath(file.getPath())).find()) {
                            filesPerPath.set(Lists.newArrayList(file));
                        } else {
                            filesPerPath.set(null);
                        }
                        currentPath.set(file.getPath());
                        if (ret != null && !ret.isEmpty()) {
                            return ret;
                        }
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .map(this::findSearchFile)
                .filter(Objects::nonNull);

        return new CloseableStream<>() {
            @Override
            public Stream<BackupFile> stream() {
                return stream;
            }

            @Override
            public void setReportErrorsAsNull(boolean reportErrorsAsNull) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close() throws IOException {
                closeableStream.close();
            }
        };
    }

    private BackupFile findSearchFile(List<BackupFile> files) {
        BackupFile file = null;
        if (timestamp == null)
            file = files.get(files.size() - 1);
        else {
            for (int i = files.size() - 1; i >= 0; i--) {
                if (files.get(i).getAdded() <= timestamp) {
                    file = files.get(i);
                } else {
                    break;
                }
            }
        }
        if (file != null && !includeDeleted) {
            if (deletedFile(file)) {
                return null;
            }
        }
        return file;
    }

    private boolean deletedFile(BackupFile file) {
        String path = file.getPath();
        String parent = ROOT;
        int ind = path.indexOf(PathNormalizer.PATH_SEPARATOR, 1);
        do {
            String child = path.substring(0, ind + 1);
            try {
                if (!directoryCache.get(parent).contains(child)) {
                    return true;
                }
            } catch (ExecutionException e) {
                throw new RuntimeException(String.format("Failed fetching contents for %s", parent), e);
            }
            parent = child;
            ind = path.indexOf(PathNormalizer.PATH_SEPARATOR, ind + 1);
        } while (ind > 0);

        return false;
    }

    public static class InterruptedSearch extends RuntimeException {
    }
}
