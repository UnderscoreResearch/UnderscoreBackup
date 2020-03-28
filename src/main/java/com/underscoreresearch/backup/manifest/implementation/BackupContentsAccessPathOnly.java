package com.underscoreresearch.backup.manifest.implementation;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

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

    public BackupContentsAccessPathOnly(MetadataRepository repository, Long timestamp) {
        this.repository = repository;
        this.timestamp = timestamp;
    }

    protected BackupDirectory getPaths(String path) throws IOException {
        BackupDirectory ret;

        if (timestamp == null) {
            ret = repository.lastDirectory(path);

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

        return ret;
    }

    protected BackupDirectory processAdditionalPaths(BackupDirectory ret) {
        return ret;
    }

    private BackupFile createFile(String root, String path) throws IOException {
        if (path.endsWith(PATH_SEPARATOR)) {
            BackupDirectory ret = pathEntry(root + path);
            if (ret == null)
                return BackupFile.builder().path(root + path).build();
            return BackupFile.builder().path(root + path).lastChanged(ret.getTimestamp()).build();
        }
        BackupFile ret = null;
        if (timestamp == null)
            ret = repository.lastFile(root + path);
        else {
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
        }

        if (ret == null) {
            return BackupFile.builder().path(path).build();
        }
        return ret;
    }

    private BackupDirectory pathEntry(String path) throws IOException {
        if (timestamp == null) {
            return repository.lastDirectory(path);
        }
        List<BackupDirectory> directories = repository.directory(path);
        BackupDirectory ret = null;
        if (directories != null) {
            for (BackupDirectory entry : directories) {
                if (entry.getTimestamp() <= timestamp) {
                    ret = entry;
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
        BackupDirectory foundPaths = getPaths(normalizedRoot);
        if (foundPaths == null && normalizedRoot.length() > 1) {
            BackupFile file = createFile("", normalizedRoot.substring(0, normalizedRoot.length() - 1));
            if (file.getLastChanged() != null) {
                return Lists.newArrayList(file);
            }
        }

        if (foundPaths == null) {
            foundPaths = new BackupDirectory(path, null, new TreeSet<>());
        }

        foundPaths = addRootPaths(foundPaths, normalizedRoot);

        if (foundPaths.getFiles().size() > 0) {
            List<BackupFile> files = new ArrayList<>();
            for (String dirPath : foundPaths.getFiles()) {
                files.add(createFile(normalizedRoot, dirPath));
            }
            return files;
        }

        return null;
    }

    protected BackupDirectory addRootPaths(BackupDirectory foundPaths, String normalizedRoot) {
        return foundPaths;
    }
}
