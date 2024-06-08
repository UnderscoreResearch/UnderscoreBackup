package com.underscoreresearch.backup.manifest.implementation;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.file.PathNormalizer.ROOT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupFile;

@Slf4j
public class BackupContentsAccessPathOnly implements BackupContentsAccess {
    private static final String EMPTY_STRING = "";
    private final MetadataRepository repository;
    private final Long timestamp;
    private final boolean includeDeleted;
    private final LoadingCache<String, String> directoryPermissions = CacheBuilder.newBuilder()
            .maximumSize(50)
            .build(new CacheLoader<String, String>() {
                @Override
                public String load(String key) throws Exception {
                    BackupDirectory directory = repository.directory(key, timestamp, false);
                    if (directory != null) {
                        return Objects.requireNonNullElse(directory.getPermissions(), EMPTY_STRING);
                    }
                    return EMPTY_STRING;
                }
            });

    public BackupContentsAccessPathOnly(MetadataRepository repository, Long timestamp, boolean includeDeleted) {
        this.repository = repository;
        this.timestamp = timestamp;
        this.includeDeleted = includeDeleted;
    }

    protected BackupDirectory getPaths(String path) throws IOException {
        BackupDirectory ret;

        ret = repository.directory(path, timestamp, includeDeleted);
        if (ret != null) {
            directoryPermissions.put(ret.getPath(), Objects.requireNonNullElse(ret.getPermissions(), EMPTY_STRING));
        }

        if (timestamp == null) {
            if (ret == null) {
                ret = new BackupDirectory(path, null, null, new TreeSet<>(), null);
            }

            ret = processAdditionalPaths(ret);
        }

        return ret;
    }

    protected BackupDirectory processAdditionalPaths(BackupDirectory ret) {
        return ret;
    }

    private BackupFile createFile(String root, String path) throws IOException {
        if (path.endsWith(PATH_SEPARATOR)) {
            BackupDirectory ret = pathEntry(root + path);
            if (ret == null || ret.getFiles().isEmpty())
                return null;
            if (!includeDeleted && ret.getDeleted() != null && (timestamp == null || ret.getDeleted() < timestamp))
                return null;
            return BackupFile.builder().path(root + path).added(ret.getAdded()).deleted(ret.getDeleted()).build();
        }
        BackupFile ret = repository.file(root + path, timestamp);

        if (ret == null) {
            return null;
        }

        if (ret.getDeleted() != null) {
            if (timestamp != null && ret.getDeleted() > timestamp) {
                ret.setDeleted(null);
            } else if (!includeDeleted) {
                return null;
            }
        }

        return ret;
    }

    private BackupFile createFileAllowMissing(String root, String path) throws IOException {
        BackupFile file = createFile(root, path);
        if (file == null) {
            return BackupFile.builder().path(root + path).build();
        }
        return file;
    }

    private BackupDirectory pathEntry(String path) throws IOException {
        return repository.directory(path, timestamp, false);
    }

    @Override
    public List<BackupFile> directoryFiles(String path) throws IOException {
        final String normalizedRoot;
        if (!path.endsWith(PATH_SEPARATOR))
            normalizedRoot = path + PATH_SEPARATOR;
        else
            normalizedRoot = path;
        FoundPath foundPaths = FoundPath.fromDirectory(getPaths(normalizedRoot));

        if (foundPaths == null && normalizedRoot.length() > 1) {
            BackupFile file = createFile("", normalizedRoot.substring(0, normalizedRoot.length() - 1));
            if (file != null) {
                return Lists.newArrayList(file);
            }
        }

        if (foundPaths == null) {
            foundPaths = new FoundPath(path, null, null, new TreeMap<>());
        }

        if (normalizedRoot.equals(ROOT) && foundPaths.getFiles().isEmpty()) {
            foundPaths = FoundPath.fromDirectory(getPaths(""));
            if (foundPaths != null) {
                List<BackupFile> files = new ArrayList<>();
                Set<String> foundRoots = new HashSet<>();
                for (String dirPath : foundPaths.getFiles().keySet()) {
                    String fullPath = dirPath;
                    int ind = fullPath.indexOf('/', 1);
                    if (ind > 0) {
                        fullPath = fullPath.substring(0, ind + 1);
                    }
                    if (foundRoots.add(fullPath)) {
                        files.add(createFileAllowMissing("", fullPath));
                    }
                }
                return files;
            }
            return null;
        }

        foundPaths = addRootPaths(foundPaths, normalizedRoot);

        if (!foundPaths.getFiles().isEmpty()) {
            List<BackupFile> files = new ArrayList<>();
            for (Map.Entry<String, Boolean> dirPath : foundPaths.getFiles().entrySet()) {
                BackupFile file = dirPath.getValue() ?
                        createFile(normalizedRoot, dirPath.getKey()) :
                        createFileAllowMissing(normalizedRoot, dirPath.getKey());
                if (file != null)
                    files.add(file);
            }
            return files;
        }

        return null;
    }

    @Override
    public String directoryPermissions(String path) throws IOException {
        try {
            if (path.isEmpty()) {
                return null;
            }
            if (!path.endsWith("/")) {
                path += "/";
            }
            String data = directoryPermissions.get(path);
            if (!Strings.isNullOrEmpty(data)) {
                return data;
            }
            return null;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw new RuntimeException(e);
        }
    }

    protected FoundPath addRootPaths(FoundPath foundPaths, String normalizedRoot) {
        return foundPaths;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    protected static class FoundPath {
        private String path;
        private String permissions;
        private Long added;
        private TreeMap<String, Boolean> files;

        protected static FoundPath fromDirectory(BackupDirectory directory) {
            if (directory != null) {
                FoundPath ret = new FoundPath();
                ret.path = directory.getPath();
                ret.added = directory.getAdded();
                ret.permissions = directory.getPermissions();
                ret.files = new TreeMap<>();
                directory.getFiles().forEach((file) -> ret.files.put(file, ret.added != null));
                return ret;
            }
            return null;
        }
    }
}
