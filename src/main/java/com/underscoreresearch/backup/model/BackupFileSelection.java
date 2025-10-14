package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.underscoreresearch.backup.file.PathNormalizer;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.file.PathNormalizer.ROOT;

/**
 * Represents the file selection criteria for a backup set.
 * Contains information about which files and directories to include or exclude in a backup.
 */

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupFileSelection {
    /**
     * List of compiled patterns for file exclusions.
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @JsonIgnore
    private List<Pattern> exclusionPatterns;
    
    /**
     * List of compiled patterns for directory exclusions.
     */
    @JsonIgnore
    private List<Pattern> exclusionDirectoryPatterns;

    /**
     * List of exclusion patterns as strings.
     */
    private List<String> exclusions;
    
    /**
     * List of root directories to include in the backup.
     */
    private List<BackupSetRoot> roots;

    /**
     * Constructor that initializes a BackupFileSelection with roots and exclusions.
     * 
     * @param roots The list of root directories to include
     * @param exclusions The list of exclusion patterns
     */
    public BackupFileSelection(@JsonProperty("roots") List<BackupSetRoot> roots,
                               @JsonProperty("exclusions") List<String> exclusions) {
        setExclusions(exclusions);

        this.roots = roots;
    }

    /**
     * Sets the exclusion patterns and compiles them into Pattern objects.
     * 
     * @param exclusions The list of exclusion patterns
     */
    public void setExclusions(List<String> exclusions) {
        if (exclusions != null) {
            exclusionDirectoryPatterns = new ArrayList<>();
            exclusionPatterns = exclusions.stream().map(t -> {
                Pattern ret = Pattern.compile(t);
                if (!t.contains("$")) {
                    exclusionDirectoryPatterns.add(ret);
                }
                return ret;
            }).collect(Collectors.toList());
            this.exclusions = ImmutableList.copyOf(exclusions);
        } else {
            exclusionDirectoryPatterns = exclusionPatterns = new ArrayList<>();
        }
    }

    /**
     * Checks if a file is excluded by any of the exclusion patterns.
     * 
     * @param finalFile The file path to check
     * @return True if the file is excluded, false otherwise
     */
    @JsonIgnore
    boolean checkExcluded(String finalFile) {
        return exclusionPatterns.stream().anyMatch(pattern -> pattern.matcher(finalFile).find());
    }

    /**
     * Checks if a directory is excluded by any of the directory exclusion patterns.
     * 
     * @param finalDirectory The directory path to check
     * @return True if the directory is excluded, false otherwise
     */
    @JsonIgnore
    private boolean checkExcludedDirectory(String finalDirectory) {
        return exclusionDirectoryPatterns.stream().anyMatch(pattern -> pattern.matcher(finalDirectory).find());
    }

    /**
     * Checks if a file should be included in the backup.
     * 
     * @param file The file path to check
     * @return True if the file should be included, false otherwise
     */
    @JsonIgnore
    public boolean includeFile(String file) {
        for (BackupSetRoot root : roots) {
            if (root.includeFile(file, this))
                return true;
        }
        return false;
    }

    /**
     * Checks if a file is within any of the root directories.
     * 
     * @param file The file path to check
     * @return True if the file is within a root directory, false otherwise
     */
    @JsonIgnore
    public boolean inRoot(String file) {
        for (BackupSetRoot root : roots) {
            if (root.inRoot(file))
                return true;
        }
        return false;
    }

    /**
     * Checks if a directory should be included in the backup.
     * 
     * @param path The directory path to check
     * @return True if the directory should be included, false otherwise
     */
    @JsonIgnore
    public boolean includeDirectory(String path) {
        for (BackupSetRoot root : roots) {
            if (root.includeDirectory(path)) {
                return !checkExcludedDirectory(path);
            }
        }
        return false;
    }

    /**
     * Checks if a path should be included for sharing.
     * 
     * @param path The path to check
     * @return True if the path should be included for sharing, false otherwise
     */
    @JsonIgnore
    public boolean includeForShare(String path) {
        if (path.endsWith(PathNormalizer.PATH_SEPARATOR)) {
            if (path.equals(ROOT) && roots.size() > 0) {
                return true;
            }
            if (!includeDirectory(path)) {
                for (BackupSetRoot root : roots) {
                    if (root.getNormalizedPath().startsWith(path)) {
                        return true;
                    }
                }
                return false;
            }
            return true;
        }
        return includeFile(path);
    }

    /**
     * Gets a string representation of all root directories.
     * 
     * @return A string containing all root directories
     */
    @JsonIgnore
    public String getAllRoots() {
        return "\"" + roots.stream().map(BackupSetRoot::getPath).collect(Collectors.joining("\", \"")) + "\"";
    }
}
