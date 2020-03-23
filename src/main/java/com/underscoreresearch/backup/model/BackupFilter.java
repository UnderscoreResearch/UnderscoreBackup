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

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
public class BackupFilter {
    private List<String> paths;
    private BackupFilterType type;
    private List<BackupFilter> children;

    public void setPaths(List<String> paths) {
        this.paths = paths.stream().map(path -> {
            if (path.endsWith(PathNormalizer.PATH_SEPARATOR))
                return path.substring(0, path.length() - PathNormalizer.PATH_SEPARATOR.length());
            else
                return path;
        }).collect(Collectors.toList());
    }

    @JsonCreator
    public BackupFilter(
            @JsonProperty("paths") List<String> paths,
            @JsonProperty("type") BackupFilterType type,
            @JsonProperty("children") List<BackupFilter> children) {
        setPaths(paths);
        this.type = type;
        this.children = children;
    }

    public String fileMatch(final String file) {
        for (String path : paths) {
            if (file.startsWith(path)) {
                if (path.length() == file.length() ||
                        file.substring(path.length(), path.length() + PathNormalizer.PATH_SEPARATOR.length()).
                                equals(PathNormalizer.PATH_SEPARATOR)) {
                    return path;
                }
            }
        }
        return null;
    }

    public boolean includeMatchedFile(final String path, final String file) {
        if (file.length() == path.length()) {
            return shouldInclude();
        }

        final String subPath = file.substring(path.length() + PathNormalizer.PATH_SEPARATOR.length());

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

    public boolean includeMatchedDirectory(String path, String file) {
        if (file.length() == path.length()) {
            return shouldInclude();
        }

        final String subPath = file.substring(path.length() + PathNormalizer.PATH_SEPARATOR.length());

        if (children != null) {
            for (BackupFilter filter : children) {
                String filterPath = filter.fileMatch(subPath);
                if (filterPath != null) {
                    return true;
                }
            }
        }

        return shouldInclude();
    }

    private boolean shouldInclude() {
        return type != BackupFilterType.EXCLUDE;
    }
}
