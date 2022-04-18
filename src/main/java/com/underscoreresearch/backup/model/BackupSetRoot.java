package com.underscoreresearch.backup.model;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;

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

    @JsonIgnore
    public boolean includeFile(String file, BackupSet set) {
        if (file.startsWith(normalizedPath)
                || (file.length() == normalizedPath.length() - PATH_SEPARATOR.length() && normalizedPath.startsWith(file))
                || normalizedPath.equals("/")) {
            if (file.endsWith(PATH_SEPARATOR)) {
                file = file.substring(0, file.length() - PATH_SEPARATOR.length());
            }

            String finalFile = file;
            if (set != null && set.checkExcluded(finalFile)) {
                return false;
            }

            if (file.length() == normalizedPath.length() - PATH_SEPARATOR.length()) {
                return true;
            } else {
                String subPath = file.substring(normalizedPath.length());
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
        if (file.startsWith(normalizedPath)
                || (file.length() == normalizedPath.length() - PATH_SEPARATOR.length() && normalizedPath.startsWith(file))) {
            return true;
        }
        return false;
    }

    @JsonIgnore
    public boolean includeDirectory(String path) {
        if (path.startsWith(normalizedPath)
                || (path.length() == normalizedPath.length() - PATH_SEPARATOR.length() && normalizedPath.startsWith(path))) {
            if (path.endsWith(PATH_SEPARATOR)) {
                path = path.substring(0, path.length() - PATH_SEPARATOR.length());
            }

            if (path.length() == normalizedPath.length() - PATH_SEPARATOR.length()) {
                return true;
            } else {
                String subPath = path.substring(normalizedPath.length());
                if (filters != null) {
                    for (BackupFilter filter : filters) {
                        String filterPath = filter.fileMatch(subPath);
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

    @JsonIgnore
    public void setNormalizedPath(String path) {
        if (path != null && !path.endsWith(PATH_SEPARATOR)) {
            this.normalizedPath = path + PATH_SEPARATOR;
        } else {
            this.normalizedPath = path;
        }
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
}
