package com.underscoreresearch.backup.model;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.file.PathNormalizer.ROOT;

import java.util.List;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.underscoreresearch.backup.file.PathNormalizer;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupSetRoot {
    @JsonIgnore
    private String normalizedPath;
    private List<BackupFilter> filters;

    @JsonCreator
    @Builder
    public BackupSetRoot(@JsonProperty("path") String path,
                         @JsonProperty("filters") List<BackupFilter> filters) {
        setPath(path);

        this.filters = filters;
    }

    private static String withFinalSeparator(String path) {
        if (path.endsWith(PATH_SEPARATOR))
            return path;
        return path + PATH_SEPARATOR;
    }

    public static String withoutFinalSeparator(String path) {
        if (path.endsWith(PATH_SEPARATOR))
            return path.substring(0, path.length() - 1);
        return path;
    }

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

            if (withoutFinalSeparator(file).length() == withoutFinalSeparator(normalizedPath).length()) {
                return true;
            } else {
                String subPath = getSubPath(file);
                if (filters != null) {
                    for (BackupFilter filter : filters) {
                        String filterPath = filter.fileMatch(subPath);
                        if (filterPath != null) {
                            return filter.includeMatchedFile(filterPath, subPath);
                        }
                    }
                }
                return true;
            }
        } else {
            return false;
        }
    }

    @JsonIgnore
    public boolean inRoot(String file) {
        return withoutFinalSeparator(file).equals(withoutFinalSeparator(normalizedPath))
                || file.startsWith(withFinalSeparator(normalizedPath))
                || normalizedPath.equals(ROOT);
    }

    @JsonIgnore
    public boolean includeDirectory(String path) {
        if (inRoot(path)) {

            if (withoutFinalSeparator(path).length() == withoutFinalSeparator(normalizedPath).length()) {
                return true;
            } else {
                String subPath = getSubPath(path);
                if (filters != null) {
                    for (BackupFilter filter : filters) {
                        String filterPath = filter.directoryMatch(subPath);
                        if (filterPath != null) {
                            return filter.includeMatchedDirectory(filterPath, subPath);
                        }
                    }
                }
                return true;
            }
        } else {
            return false;
        }
    }

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

    @JsonIgnore
    public void setNormalizedPath(String path) {
        this.normalizedPath = path;
    }

    public String getPath() {
        return PathNormalizer.physicalPath(normalizedPath);
    }

    public void setPath(String path) {
        if (path != null) {
            setNormalizedPath(PathNormalizer.normalizePath(path));
        } else {
            normalizedPath = path;
        }
    }

    public boolean includeFileOrDirectory(BackupFile file) {
        if (file.isDirectory())
            return includeDirectory(file.getPath());
        else
            return includeFile(file.getPath(), null);
    }
}
