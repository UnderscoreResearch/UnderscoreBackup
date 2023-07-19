package com.underscoreresearch.backup.manifest.implementation;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.file.PathNormalizer.ROOT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupFile;

@Slf4j
public class BackupContentsAccessPathOnly implements BackupContentsAccess {
    private final MetadataRepository repository;
    private final Long timestamp;
    private final boolean includeDeleted;
    public BackupContentsAccessPathOnly(MetadataRepository repository, Long timestamp, boolean includeDeleted) {
        this.repository = repository;
        this.timestamp = timestamp;
        this.includeDeleted = includeDeleted;
    }

    protected BackupDirectory getPaths(String path) throws IOException {
        BackupDirectory ret;

        if (timestamp == null) {
            ret = repository.directory(path, timestamp, false);

            if (ret == null) {
                ret = new BackupDirectory(path, null, new TreeSet<>());
            }

            ret = processAdditionalPaths(ret);
        } else {
            BackupDirectory entry = pathEntry(path);
            if (entry != null) {
                ret = entry;
            } else {
                ret = null;
            }
        }

        if (ret != null && includeDeleted) {
            BackupDirectory allDir = repository.directory(path, timestamp, true);
            if (allDir != null) {
                ret.getFiles().addAll(allDir.getFiles());
            }
        }

        return ret;
    }

    protected BackupDirectory processAdditionalPaths(BackupDirectory ret) {
        return ret;
    }

    private BackupFile createFile(String root, String path, boolean allowMissing) throws IOException {
        if (path.endsWith(PATH_SEPARATOR)) {
            BackupDirectory ret = pathEntry(root + path);
            if (ret == null)
                return allowMissing ? BackupFile.builder().path(root + path).build() : null;
            return BackupFile.builder().path(root + path).added(ret.getAdded()).build();
        }
        BackupFile ret = repository.file(root + path, timestamp);

        if (ret == null) {
            return allowMissing ? BackupFile.builder().path(root + path).build() : null;
        }
        return ret;
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
            BackupFile file = createFile("", normalizedRoot.substring(0, normalizedRoot.length() - 1), false);
            if (file != null) {
                return Lists.newArrayList(file);
            }
        }

        if (foundPaths == null) {
            foundPaths = new FoundPath(path, null, new TreeMap<>());
        }

        if (normalizedRoot.equals(ROOT) && foundPaths.getFiles().size() == 0) {
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
                        files.add(createFile("", fullPath, true));
                    }
                }
                return files;
            }
            return null;
        }

        foundPaths = addRootPaths(foundPaths, normalizedRoot);

        if (foundPaths.getFiles().size() > 0) {
            List<BackupFile> files = new ArrayList<>();
            for (Map.Entry<String, Boolean> dirPath : foundPaths.getFiles().entrySet()) {
                BackupFile file = createFile(normalizedRoot, dirPath.getKey(), !dirPath.getValue());
                if (file != null)
                    files.add(file);
            }
            return files;
        }

        return null;
    }

    protected FoundPath addRootPaths(FoundPath foundPaths, String normalizedRoot) {
        return foundPaths;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    protected static class FoundPath {
        private String path;
        private Long added;
        private TreeMap<String, Boolean> files;

        protected static FoundPath fromDirectory(BackupDirectory directory) {
            if (directory != null) {
                FoundPath ret = new FoundPath();
                ret.path = directory.getPath();
                ret.added = directory.getAdded();
                ret.files = new TreeMap<>();
                directory.getFiles().forEach((file) -> ret.files.put(file, ret.added != null));
                return ret;
            }
            return null;
        }
    }
}
