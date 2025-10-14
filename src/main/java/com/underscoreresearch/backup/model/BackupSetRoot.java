package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.underscoreresearch.backup.file.PathNormalizer;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.file.PathNormalizer.ROOT;

/**
 * Represents a root directory in a backup set.
 * Contains information about a root path and filters to apply to files and directories within it.
 */

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupSetRoot {
    /**
     * The normalized path of this root directory.
     */
    @JsonIgnore
    private String normalizedPath;
    
    /**
     * List of filters to apply to files and directories within this root.
     */
    private List<BackupFilter> filters;

    /**
     * Constructor that initializes a BackupSetRoot with a path and filters.
     * 
     * @param path The path of this root directory
     * @param filters The list of filters to apply
     */
    @JsonCreator
    @Builder
    public BackupSetRoot(@JsonProperty("path") String path,
                         @JsonProperty("filters") List<BackupFilter> filters) {
        setPath(path);

        this.filters = filters;
    }

    /**
     * Adds a path separator to the end of a path if it doesn't already have one.
     * 
     * @param path The path to modify
     * @return The path with a separator at the end
     */
    private static String withFinalSeparator(String path) {
        if (path.endsWith(PATH_SEPARATOR))
            return path;
        return path + PATH_SEPARATOR;
    }

    /**
     * Removes the path separator from the end of a path if it has one.
     * 
     * @param path The path to modify
     * @return The path without a separator at the end
     */
    public static String withoutFinalSeparator(String path) {
        if (path.endsWith(PATH_SEPARATOR))
            return path.substring(0, path.length() - 1);
        return path;
    }

    /**
     * Checks if a file should be included in the backup.
     * 
     * @param file The file path to check
     * @param set The backup file selection to use for exclusion checks
     * @return True if the file should be included, false otherwise
     */
    @JsonIgnore
    public boolean includeFile(String file, BackupFileSelection set) {
        if (inRoot(file)) {
            if (file.endsWith(PATH_SEPARATOR)) {
                file = file.substring(0, file.length() - PATH_SEPARATOR.length());
            }

            String finalFile = file;
            if (set != null && set.checkExcluded(finalFile)) {
                return false;
            }

            if (withoutFinalSeparator(file).length() != withoutFinalSeparator(normalizedPath).length()) {
                String subPath = getSubPath(file);
                if (filters != null) {
                    for (BackupFilter filter : filters) {
                        String filterPath = filter.fileMatch(subPath);
                        if (filterPath != null) {
                            return filter.includeMatchedFile(filterPath, subPath);
                        }
                    }
                }
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks if a file is within this root directory.
     * 
     * @param file The file path to check
     * @return True if the file is within this root directory, false otherwise
     */
    @JsonIgnore
    public boolean inRoot(String file) {
        return withoutFinalSeparator(file).equals(withoutFinalSeparator(normalizedPath))
                || file.startsWith(withFinalSeparator(normalizedPath))
                || normalizedPath.equals(ROOT);
    }

    /**
     * Checks if a directory should be included in the backup.
     * 
     * @param path The directory path to check
     * @return True if the directory should be included, false otherwise
     */
    @JsonIgnore
    public boolean includeDirectory(String path) {
        if (inRoot(path)) {

            if (withoutFinalSeparator(path).length() != withoutFinalSeparator(normalizedPath).length()) {
                String subPath = getSubPath(path);
                if (filters != null) {
                    for (BackupFilter filter : filters) {
                        String filterPath = filter.directoryMatch(subPath);
                        if (filterPath != null) {
                            return filter.includeMatchedDirectory(filterPath, subPath);
                        }
                    }
                }
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Gets the sub-path of a path relative to this root directory.
     * 
     * @param path The path to get the sub-path of
     * @return The sub-path
     */
    private String getSubPath(String path) {
        String subPath;
        if (path.startsWith(normalizedPath)) {
            subPath = path.substring(normalizedPath.length());
        } else {
            subPath = path;
        }
        if (subPath.startsWith(PATH_SEPARATOR)) {
            subPath = subPath.substring(1);
        }
        return subPath;
    }

    /**
     * Sets the normalized path of this root directory.
     * 
     * @param path The normalized path to set
     */
    @JsonIgnore
    public void setNormalizedPath(String path) {
        this.normalizedPath = path;
    }

    /**
     * Gets the physical path of this root directory.
     * 
     * @return The physical path
     */
    public String getPath() {
        return PathNormalizer.physicalPath(normalizedPath);
    }

    /**
     * Sets the path of this root directory, normalizing it.
     * 
     * @param path The path to set
     */
    public void setPath(String path) {
        if (path != null) {
            setNormalizedPath(PathNormalizer.normalizePath(path));
        } else {
            normalizedPath = null;
        }
    }

    /**
     * Checks if a file or directory should be included in the backup.
     * 
     * @param file The file to check
     * @return True if the file or directory should be included, false otherwise
     */
    public boolean includeFileOrDirectory(BackupFile file) {
        if (file.isDirectory())
            return includeDirectory(file.getPath());
        else
            return includeFile(file.getPath(), null);
    }
}
