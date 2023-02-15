package com.underscoreresearch.backup.io.implementation;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.manifest.implementation.BaseManifestManagerImpl.LOG_ROOT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.underscoreresearch.backup.io.IOIndex;

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

    public static List<String> getListOfLogFiles(String lastSyncedFile, IOIndex index) throws IOException {
        List<String> days = getListOfLogFiles(lastSyncedFile, index, LOG_ROOT, true);
        List<String> files = new ArrayList<>();
        for (String day : days) {
            files.addAll(getListOfLogFiles(lastSyncedFile, index, day, false));
        }
        return files;
    }

    public static boolean rebuildAvailable(IOIndex index) throws IOException {
        List<String> files = index.availableKeys("");
        return files.contains("configuration.json") && files.contains("publickey.json");
    }
}
