package com.underscoreresearch.backup.io.implementation;

import com.underscoreresearch.backup.io.IOIndex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.manifest.implementation.BaseManifestManagerImpl.LOG_ROOT;
import static com.underscoreresearch.backup.manifest.implementation.BaseManifestManagerImpl.PUBLICKEY_FILENAME;
import static com.underscoreresearch.backup.manifest.implementation.ManifestManagerImpl.CONFIGURATION_FILENAME;

/**
 * Utility class for IO operations.
 * Provides helper methods for working with log files and checking repository availability.
 */
public class Utils {

    /**
     * Get a list of log files from a specific parent directory.
     *
     * @param lastSyncedFile The last synced log file, or null for all
     * @param index The IO index to use
     * @param parent The parent directory to search in
     * @param partial Whether to include partial matches
     * @return List of log files
     * @throws IOException If there's an error accessing the storage
     */
    private static List<String> getListOfLogFiles(String lastSyncedFile, IOIndex index, String parent, boolean partial)
            throws IOException {
        final String parentPrefix;
        if (!parent.endsWith(PATH_SEPARATOR)) {
            parentPrefix = parent + PATH_SEPARATOR;
        } else {
            parentPrefix = parent;
        }
        List<String> files = index.availableKeys(parent).stream().map(file -> parentPrefix + file)
                .sorted().collect(Collectors.toList());

        if (lastSyncedFile != null) {
            files = files.stream()
                    .filter(file -> file.compareTo(lastSyncedFile.length() > file.length() ?
                            lastSyncedFile.substring(0, file.length()) :
                            lastSyncedFile) >= (partial ? 0 : 1))
                    .collect(Collectors.toList());
        }
        return files;
    }

    /**
     * Get a list of log files after the specified log file.
     *
     * @param lastSyncedFile The last synced log file, or null for all
     * @param index The IO index to use
     * @param all Whether to include all log files
     * @return List of log files
     * @throws IOException If there's an error accessing the storage
     */
    public static List<String> getListOfLogFiles(String lastSyncedFile, IOIndex index, boolean all) throws IOException {
        List<String> days = getListOfLogFiles(lastSyncedFile, index, LOG_ROOT, true);
        List<String> files = new ArrayList<>();
        for (String day : days) {
            files.addAll(getListOfLogFiles(lastSyncedFile, index, day, false));
            if (!files.isEmpty() && !all) {
                break;
            }
        }
        return files;
    }

    /**
     * Check if a repository can be rebuilt from the available keys.
     *
     * @param index The IO index to check
     * @return True if rebuild is available, false otherwise
     * @throws IOException If there's an error checking rebuild availability
     */
    public static boolean rebuildAvailable(IOIndex index) throws IOException {
        List<String> files = index.availableKeys("");
        return files.contains(CONFIGURATION_FILENAME) && files.contains(PUBLICKEY_FILENAME);
    }
}
