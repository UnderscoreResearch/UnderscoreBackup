package com.underscoreresearch.backup.model;

import static com.underscoreresearch.backup.model.BackupSetRoot.withoutFinalSeparator;

import java.util.List;
import java.util.stream.Collectors;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.underscoreresearch.backup.file.PathNormalizer;

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

    public String fileMatch(String file) {
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

    public String directoryMatch(String file) {
        file = withoutFinalSeparator(file);
        String ret = fileMatch(file);
        if (ret != null) {
            return ret;
        }
        for (String path : paths) {
            if (path.startsWith(file)) {
                if (path.length() == file.length() ||
                        path.substring(file.length(), file.length() + PathNormalizer.PATH_SEPARATOR.length()).
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

        int subLength = path.length() + PathNormalizer.PATH_SEPARATOR.length();
        final String subPath = file.substring(Math.min(file.length(), subLength));

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
            if (subPath.length() == 0) {
                return true;
            }
            for (BackupFilter filter : children) {
                String filterPath = filter.directoryMatch(subPath);
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
