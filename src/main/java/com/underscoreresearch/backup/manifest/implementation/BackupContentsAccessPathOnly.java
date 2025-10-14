package com.underscoreresearch.backup.manifest.implementation;

import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupFile;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.file.PathNormalizer.ROOT;

/**
 * Base implementation of backup contents access that only uses path information.
 * Provides methods for accessing backup directories and files.
 */
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

    /**
     * Constructor for BackupContentsAccessPathOnly.
     *
     * @param repository The metadata repository
     * @param timestamp The timestamp to use for filtering, or null for current state
     * @param includeDeleted Whether to include deleted files
     */
    public BackupContentsAccessPathOnly(MetadataRepository repository, Long timestamp, boolean includeDeleted) {
        this.repository = repository;
        this.timestamp = timestamp;
        this.includeDeleted = includeDeleted;
    }

    /**
     * Get paths for a directory.
     *
     * @param path The directory path
     * @return The directory information, or null if not found
     * @throws IOException If there's an error accessing the repository
     */
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

    /**
     * Process additional paths for a directory.
     * This is a hook for subclasses to add additional paths.
     *
     * @param ret The directory to process
     * @return The processed directory
     */
    protected BackupDirectory processAdditionalPaths(BackupDirectory ret) {
        return ret;
    }

    /**
     * Create a file object from a path.
     *
     * @param root The root path
     * @param path The file path
     * @return The file object, or null if not found or deleted
     * @throws IOException If there's an error accessing the repository
     */
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

    /**
     * Create a file object from a path, allowing missing files.
     *
     * @param root The root path
     * @param path The file path
     * @return The file object, or a placeholder if not found
     * @throws IOException If there's an error accessing the repository
     */
    private BackupFile createFileAllowMissing(String root, String path) throws IOException {
        BackupFile file = createFile(root, path);
        if (file == null) {
            return BackupFile.builder().path(root + path).build();
        }
        return file;
    }

    /**
     * Get a directory entry from the repository.
     *
     * @param path The directory path
     * @return The directory information, or null if not found
     * @throws IOException If there's an error accessing the repository
     */
    private BackupDirectory pathEntry(String path) throws IOException {
        return repository.directory(path, timestamp, false);
    }

    /**
     * Get the files in a directory.
     *
     * @param path The directory path
     * @return List of files in the directory, or null if the directory doesn't exist
     * @throws IOException If there's an error accessing the repository
     */
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

    /**
     * Get the permissions for a directory.
     *
     * @param path The directory path
     * @return The permissions string, or null if not found
     * @throws IOException If there's an error accessing the repository
     */
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

    /**
     * Add root paths to the found paths.
     * This is a hook for subclasses to add additional root paths.
     *
     * @param foundPaths The found paths to add to
     * @param normalizedRoot The normalized root path
     * @return The updated found paths
     */
    protected FoundPath addRootPaths(FoundPath foundPaths, String normalizedRoot) {
        return foundPaths;
    }

    /**
     * Data class for found paths in a directory.
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    protected static class FoundPath {
        private String path;
        private String permissions;
        private Long added;
        private TreeMap<String, Boolean> files;

        /**
         * Create a FoundPath from a BackupDirectory.
         *
         * @param directory The directory to convert
         * @return The FoundPath, or null if the directory is null
         */
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
