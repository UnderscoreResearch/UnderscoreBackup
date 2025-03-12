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

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
public class BackupFilter {
    private List<String> paths;
    private BackupFilterType type;
    private List<BackupFilter> children;

    @JsonCreator
    public BackupFilter(
            @JsonProperty("paths") List<String> paths,
            @JsonProperty("type") BackupFilterType type,
            @JsonProperty("children") List<BackupFilter> children) {
        setPaths(paths);
        this.type = type;
        this.children = children;
    }

    public void setPaths(List<String> paths) {
        this.paths = paths.stream().map(path -> {
            if (path.endsWith(PathNormalizer.PATH_SEPARATOR))
                return path.substring(0, path.length() - PathNormalizer.PATH_SEPARATOR.length());
            else
                return path;
        }).collect(Collectors.toList());
    }

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

    private boolean shouldInclude() {
        return type != BackupFilterType.EXCLUDE;
    }
}
