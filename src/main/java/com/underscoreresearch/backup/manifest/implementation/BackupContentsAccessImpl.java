package com.underscoreresearch.backup.manifest.implementation;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.model.BackupActivePath.stripPath;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupActiveStatus;

@Slf4j
public class BackupContentsAccessImpl extends BackupContentsAccessPathOnly {
    private Map<String, BackupActivePath> activePaths;
    private Set<String> rootPaths;

    public BackupContentsAccessImpl(MetadataRepository repository, Long timestamp, boolean includeDeleted)
            throws IOException {
        super(repository, timestamp, includeDeleted);

        if (timestamp == null)
            activePaths = repository.getActivePaths(null);
        else
            activePaths = null;

        BackupDirectory rootDirectory = getPaths("");
        if (rootDirectory != null) {
            rootPaths = rootDirectory.getFiles();
        }
    }

    @Override
    protected BackupDirectory processAdditionalPaths(BackupDirectory ret) {
        if (activePaths != null) {
            BackupActivePath activePath = activePaths.get(ret.getPath());
            if (activePath != null) {
                activePath.getFiles().stream()
                        .filter(t -> (t.getPath().endsWith(PATH_SEPARATOR)
                                && t.getStatus() != BackupActiveStatus.EXCLUDED)
                                || t.getStatus() == BackupActiveStatus.INCLUDED)
                        .map(t -> stripPath(t.getPath()))
                        .forEach(ret.getFiles()::add);
            }
        }
        return ret;
    }

    @Override
    protected FoundPath addRootPaths(FoundPath foundPaths, String normalizedRoot) {
        if (rootPaths != null) {
            for (String rootPath : rootPaths) {
                if (rootPath.startsWith(normalizedRoot)) {
                    int ind = rootPath.indexOf(PATH_SEPARATOR, normalizedRoot.length());
                    if (ind >= 0) {
                        foundPaths.getFiles().put(rootPath.substring(normalizedRoot.length(), ind + 1), false);
                    } else if (rootPath.length() > normalizedRoot.length()) {
                        foundPaths.getFiles().put(rootPath.substring(normalizedRoot.length()), false);
                    }
                }
            }
        }

        return foundPaths;
    }
}
