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

public class Utils {

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

    public static boolean rebuildAvailable(IOIndex index) throws IOException {
        List<String> files = index.availableKeys("");
        return files.contains(CONFIGURATION_FILENAME) && files.contains(PUBLICKEY_FILENAME);
    }
}
