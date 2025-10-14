package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;

/**
 * Represents a path that is actively being tracked by the backup system.
 * Contains information about files within this path and their current status in the backup process.
 * Provides methods for managing and querying the files within this path.
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@EqualsAndHashCode(exclude = "savedRealPath")
@ToString
public class BackupActivePath {
    /**
     * Map of file paths to their corresponding BackupActiveFile objects.
     */
    @JsonIgnore
    private Map<String, BackupActiveFile> files = new HashMap<>();

    /**
     * List of set IDs associated with this active path.
     */
    @JsonIgnore
    @Getter
    @Setter
    private List<String> setIds;

    /**
     * Flag indicating whether this path has been processed.
     */
    @JsonIgnore
    @Getter
    @Setter
    private boolean unprocessed;

    /**
     * The saved real path for this active path.
     */
    @JsonProperty
    @Getter
    @Setter
    private String savedRealPath;

    /**
     * Constructor that initializes a BackupActivePath with a parent path and a set of files.
     * 
     * @param parent The parent path
     * @param files The set of files to include in this active path
     */
    public BackupActivePath(String parent, Set<BackupActiveFile> files) {
        String realParent;
        if (parent.length() > 0 && !parent.endsWith(PATH_SEPARATOR))
            realParent = parent + PATH_SEPARATOR;
        else
            realParent = parent;

        this.files = new HashMap<>();
        for (BackupActiveFile item : files) {
            this.files.put(realParent + item.getPath(), item);
        }
    }

    /**
     * Strips the parent path from a file path, returning just the file name or last directory.
     * 
     * @param path The path to strip
     * @return The stripped path
     */
    public static String stripPath(String path) {
        int ind;
        if (path.endsWith(PATH_SEPARATOR))
            ind = path.lastIndexOf(PATH_SEPARATOR, path.length() - PATH_SEPARATOR.length() - 1);
        else
            ind = path.lastIndexOf(PATH_SEPARATOR);
        if (ind >= 0)
            return path.substring(ind + PATH_SEPARATOR.length());
        return path;
    }

    /**
     * Finds the parent path of a given path.
     * 
     * @param path The path to find the parent of
     * @return The parent path, or null if there is no parent
     */
    public static String findParent(String path) {
        int ind;
        if (path.endsWith(PATH_SEPARATOR))
            ind = path.lastIndexOf(PATH_SEPARATOR, path.length() - PATH_SEPARATOR.length() - 1);
        else
            ind = path.lastIndexOf(PATH_SEPARATOR);
        if (ind >= 0)
            return path.substring(0, ind + PATH_SEPARATOR.length());
        return null;
    }

    /**
     * Gets the set of files in this active path.
     * 
     * @return The set of files
     */
    @JsonProperty
    public Set<BackupActiveFile> getFiles() {
        return new HashSet<>(files.values());
    }

    /**
     * Sets the files in this active path.
     * 
     * @param files The set of files to set
     */
    @JsonProperty
    public void setFiles(Set<BackupActiveFile> files) {
        this.files = files.stream().collect(Collectors.toMap(BackupActiveFile::getPath,
                t -> t));
    }

    /**
     * Sets the parent path for all files in this active path.
     * 
     * @param parent The parent path to set
     */
    @JsonIgnore
    public void setParentPath(String parent) {
        String realParent;
        if (!parent.endsWith(PATH_SEPARATOR))
            realParent = parent + PATH_SEPARATOR;
        else
            realParent = parent;

        files = files.entrySet().stream().collect(Collectors.toMap(t -> realParent + t.getValue().getPath(),
                Map.Entry::getValue));
    }

    /**
     * Adds a file to this active path.
     * 
     * @param file The file to add
     */
    @JsonIgnore
    public void addFile(BackupFile file) {
        files.put(file.getPath(), BackupActiveFile.builder().path(stripPath(file.getPath())).build());
    }

    /**
     * Checks if a file is unprocessed.
     * 
     * @param file The file path to check
     * @return True if the file is unprocessed, false otherwise
     */
    @JsonIgnore
    public boolean unprocessedFile(String file) {
        BackupActiveFile activeFile = getFile(file);
        if (activeFile == null)
            return false;
        return activeFile.getStatus() == null || activeFile.getStatus() == BackupActiveStatus.INCOMPLETE;
    }

    /**
     * Gets the active file for a backup file.
     * 
     * @param file The backup file to get the active file for
     * @return The active file, or null if not found
     */
    @JsonIgnore
    public BackupActiveFile getFile(BackupFile file) {
        return getFile(file.getPath());
    }

    /**
     * Gets the active file for a file path.
     * 
     * @param file The file path to get the active file for
     * @return The active file, or null if not found
     */
    @JsonIgnore
    public BackupActiveFile getFile(String file) {
        return files.get(file);
    }

    /**
     * Checks if all files in this active path have been completed.
     * 
     * @return True if all files are completed, false otherwise
     */
    @JsonIgnore
    public boolean completed() {
        return files.values().stream()
                .noneMatch(t -> t.getStatus() == null || t.getStatus() == BackupActiveStatus.INCOMPLETE);
    }

    /**
     * Gets the set of paths that are included in this active path.
     * 
     * @return The set of included paths
     */
    @JsonIgnore
    public Set<String> includedPaths() {
        return files.values().stream()
                .filter(backupActiveFile -> backupActiveFile.getStatus() == BackupActiveStatus.INCLUDED)
                .map(BackupActiveFile::getPath)
                .collect(Collectors.toSet());
    }

    /**
     * Merges changes from another active path into this one.
     * 
     * @param otherPaths The other active path to merge changes from
     */
    public void mergeChanges(BackupActivePath otherPaths) {
        getSetIds().addAll(otherPaths.getSetIds());
        for (Map.Entry<String, BackupActiveFile> entry : otherPaths.files.entrySet()) {
            BackupActiveFile existingFile = files.get(entry.getKey());
            if (existingFile == null || existingFile.getStatus() == BackupActiveStatus.EXCLUDED) {
                files.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
