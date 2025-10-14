package com.underscoreresearch.backup.manifest.implementation;

import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupActiveStatus;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.model.BackupActivePath.stripPath;

/**
 * Implementation of backup contents access that includes active paths.
 * Extends BackupContentsAccessPathOnly to add support for active paths.
 */
@Slf4j
public class BackupContentsAccessImpl extends BackupContentsAccessPathOnly {
    private final Map<String, BackupActivePath> activePaths;
    private Set<String> rootPaths;

    /**
     * Constructor for BackupContentsAccessImpl.
     *
     * @param repository The metadata repository
     * @param timestamp The timestamp to use for filtering, or null for current state
     * @param includeDeleted Whether to include deleted files
     * @throws IOException If there's an error accessing the repository
     */
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

    /**
     * Process additional paths from active paths.
     * Adds active paths to the directory listing.
     *
     * @param ret The directory to process
     * @return The processed directory
     */
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

    /**
     * Add root paths to the found paths.
     * Filters root paths based on the normalized root.
     *
     * @param foundPaths The found paths to add to
     * @param normalizedRoot The normalized root path
     * @return The updated found paths
     */
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
