package com.underscoreresearch.backup.manifest.implementation;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.model.BackupActivePath.stripPath;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

import lombok.extern.slf4j.Slf4j;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupActiveStatus;
import com.underscoreresearch.backup.model.BackupFile;

@Slf4j
public class BackupContentsAccessImpl implements BackupContentsAccess {
    private final MetadataRepository repository;
    private final FileSystemAccess fileSystemAccess;
    private final Long timestamp;
    private Map<String, BackupActivePath> activePaths;
    private Set<String> rootPaths;

    public BackupContentsAccessImpl(MetadataRepository repository, FileSystemAccess fileSystemAccess, Long timestamp) throws IOException {
        this.repository = repository;
        this.timestamp = timestamp;
        this.fileSystemAccess = fileSystemAccess;

        if (timestamp == null)
            activePaths = repository.getActivePaths(null);
        else
            activePaths = null;

        rootPaths = getPaths("");
    }

    private NavigableSet<String> getPaths(String path) throws IOException {
        NavigableSet<String> ret;

        if (timestamp == null) {
            ret = repository.lastDirectory(path);

            if (activePaths != null) {
                BackupActivePath activePath = activePaths.get(path);
                if (activePath != null) {
                    if (ret == null)
                        ret = new TreeSet<>();

                    activePath.getFiles().stream()
                            .filter(t -> (t.getPath().endsWith(PATH_SEPARATOR)
                                    && t.getStatus() != BackupActiveStatus.EXCLUDED)
                                    || t.getStatus() == BackupActiveStatus.INCLUDED)
                            .map(t -> stripPath(t.getPath()))
                            .forEach(ret::add);
                }
            }
        } else {
            NavigableMap<Long, NavigableSet<String>> set = repository.directory(path);

            ret = null;
            if (set != null) {
                for (Map.Entry<Long, NavigableSet<String>> entry : set.entrySet()) {
                    if (entry.getKey() <= timestamp) {
                        ret = entry.getValue();
                    } else {
                        break;
                    }
                }
            }
        }

        return ret;
    }

    private BackupFile createFile(String root, String path) throws IOException {
        if (path.endsWith(PATH_SEPARATOR)) {
            return BackupFile.builder().path(root + path).build();
        }
        if (timestamp == null)
            return repository.lastFile(root + path);

        BackupFile ret = null;
        List<BackupFile> files = repository.file(root + path);
        if (files != null) {
            for (BackupFile file : files) {
                if (file.getLastChanged() <= timestamp) {
                    ret = file;
                } else {
                    break;
                }
            }
        }

        return ret;
    }

    @Override
    public List<BackupFile> directoryFiles(String path) throws IOException {
        final String normalizedRoot;
        if (!path.endsWith(PATH_SEPARATOR))
            normalizedRoot = path + PATH_SEPARATOR;
        else
            normalizedRoot = path;
        Set<String> foundPaths = getPaths(normalizedRoot);
        if (foundPaths == null && normalizedRoot.length() > 1) {
            BackupFile file = createFile("", normalizedRoot.substring(0, normalizedRoot.length() - 1));
            if (file != null) {
                return Lists.newArrayList(file);
            }
        }

        if (rootPaths != null) {
            for (String rootPath : rootPaths) {
                if (rootPath.startsWith(normalizedRoot)) {
                    if (foundPaths == null) {
                        foundPaths = new TreeSet<>();
                    }

                    int ind = rootPath.indexOf(PATH_SEPARATOR, normalizedRoot.length());
                    if (ind >= 0) {
                        foundPaths.add(rootPath.substring(normalizedRoot.length(), ind + 1));
                    }
                }
            }
        }

        if (foundPaths != null) {
            List<BackupFile> files = new ArrayList<>();
            for (String dirPath : foundPaths) {
                files.add(createFile(normalizedRoot, dirPath));
            }
            return files;
        }

        return null;
    }
}
