package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.underscoreresearch.backup.file.PathNormalizer;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.model.BackupSetRoot.withoutFinalSeparator;

/**
 * Represents a filter for backup files and directories.
 * Contains information about paths to include or exclude in a backup,
 * and can have child filters for more specific filtering.
 */

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
public class BackupFilter {
    /**
     * List of paths to filter.
     */
    private List<String> paths;
    
    /**
     * Type of filter (include or exclude).
     */
    private BackupFilterType type;
    
    /**
     * List of child filters.
     */
    private List<BackupFilter> children;

    /**
     * Constructor that initializes a BackupFilter with paths, type, and children.
     * 
     * @param paths The list of paths to filter
     * @param type The type of filter
     * @param children The list of child filters
     */
    @JsonCreator
    public BackupFilter(
            @JsonProperty("paths") List<String> paths,
            @JsonProperty("type") BackupFilterType type,
            @JsonProperty("children") List<BackupFilter> children) {
        setPaths(paths);
        this.type = type;
        this.children = children;
    }

    /**
     * Sets the paths to filter, normalizing them by removing trailing separators.
     * 
     * @param paths The list of paths to filter
     */
    public void setPaths(List<String> paths) {
        this.paths = paths.stream().map(path -> {
            if (path.endsWith(PathNormalizer.PATH_SEPARATOR))
                return path.substring(0, path.length() - PathNormalizer.PATH_SEPARATOR.length());
            else
                return path;
        }).collect(Collectors.toList());
    }

    /**
     * Checks if a file matches any of the filter paths.
     * 
     * @param file The file path to check
     * @return The matching path, or null if no match
     */
    public String fileMatch(String file) {
        for (String path : paths) {
            if (file.startsWith(path)) {
                if (path.length() == file.length() ||
                        file.startsWith(PathNormalizer.PATH_SEPARATOR, path.length())) {
                    return path;
                }
            }
        }
        return null;
    }

    /**
     * Checks if a directory matches any of the filter paths.
     * 
     * @param file The directory path to check
     * @return The matching path, or null if no match
     */
    public String directoryMatch(String file) {
        file = withoutFinalSeparator(file);
        String ret = fileMatch(file);
        if (ret != null) {
            return ret;
        }
        for (String path : paths) {
            if (path.startsWith(file)) {
                if (path.length() == file.length() ||
                        path.startsWith(PathNormalizer.PATH_SEPARATOR, file.length())) {
                    return path;
                }
            }
        }
        return null;
    }

    /**
     * Checks if a matched file should be included in the backup.
     * 
     * @param path The matched path
     * @param file The file path to check
     * @return True if the file should be included, false otherwise
     */
    public boolean includeMatchedFile(final String path, final String file) {
        String pathWithoutSeparator = withoutFinalSeparator(path);

        if (withoutFinalSeparator(file).length() == pathWithoutSeparator.length()) {
            return shouldInclude();
        }

        final String subPath = file.substring(Math.min(file.length(),
                pathWithoutSeparator.length() + PathNormalizer.PATH_SEPARATOR.length()));

        if (children != null) {
            for (BackupFilter filter : children) {
                String filterPath = filter.fileMatch(subPath);
                if (filterPath != null) {
                    return filter.includeMatchedFile(filterPath, subPath);
                }
            }
        }

        return shouldInclude();
    }

    /**
     * Checks if a matched directory should be included in the backup.
     * 
     * @param path The matched path
     * @param file The directory path to check
     * @return True if the directory should be included, false otherwise
     */
    public boolean includeMatchedDirectory(String path, String file) {
        String pathWithoutSeparator = withoutFinalSeparator(path);
        String fileWithoutSeparator = withoutFinalSeparator(file);
        final String subPath = file.substring(Math.min(file.length(),
                pathWithoutSeparator.length() + PathNormalizer.PATH_SEPARATOR.length()));

        if (children != null) {
            if (subPath.length() == 0) {
                return true;
            }
            for (BackupFilter filter : children) {
                String filterPath = filter.directoryMatch(subPath);
                if (filterPath != null) {
                    return filter.includeMatchedDirectory(filterPath, subPath);
                }
            }
        }

        if (pathWithoutSeparator.length() > fileWithoutSeparator.length())
            return true;

        if (fileWithoutSeparator.length() == pathWithoutSeparator.length())
            return shouldInclude();

        return shouldInclude();
    }

    /**
     * Checks if this filter should include paths.
     * 
     * @return True if this filter should include paths, false otherwise
     */
    private boolean shouldInclude() {
        return type != BackupFilterType.EXCLUDE;
    }
}
