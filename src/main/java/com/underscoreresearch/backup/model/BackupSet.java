package com.underscoreresearch.backup.model;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.underscoreresearch.backup.file.PathNormalizer;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupSet {
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @JsonIgnore
    private List<Pattern> exclusionPatterns;

    private String id;
    @JsonIgnore
    private String normalizedRoot;
    private List<String> exclusions;
    private String schedule;
    private List<BackupFilter> filters;
    private List<String> destinations;

    public void setNormalizedRoot(String root) {
        if (root != null && !root.endsWith(PATH_SEPARATOR)) {
            this.normalizedRoot = root + PATH_SEPARATOR;
        } else {
            this.normalizedRoot = root;
        }
    }

    public String getRoot() {
        return PathNormalizer.physicalPath(normalizedRoot);
    }

    public void setRoot(String root) {
        if (root != null) {
            setNormalizedRoot(PathNormalizer.normalizePath(root));
        } else {
            normalizedRoot = root;
        }
    }

    public void setExclusions(List<String> exclusions) {
        if (exclusions != null) {
            exclusionPatterns = exclusions.stream().map(t -> Pattern.compile(t)).collect(Collectors.toList());
            this.exclusions = ImmutableList.copyOf(exclusions);
        } else {
            exclusionPatterns = new ArrayList<>();
        }
    }

    @JsonCreator
    @Builder
    public BackupSet(@JsonProperty("id") String id,
                     @JsonProperty("root") String root,
                     @JsonProperty("exclusions") List<String> exclusions,
                     @JsonProperty("schedule") String schedule,
                     @JsonProperty("filters") List<BackupFilter> filters,
                     @JsonProperty("destinations") List<String> destinations) {
        setRoot(PathNormalizer.normalizePath(root));
        setExclusions(exclusions);

        this.id = id;
        this.schedule = schedule;
        this.filters = filters;
        this.destinations = destinations;
    }

    @JsonIgnore
    public boolean includeFile(String file) {
        if (file.startsWith(normalizedRoot)
                || (file.length() == normalizedRoot.length() - PATH_SEPARATOR.length() && normalizedRoot.startsWith(file))) {
            if (file.endsWith(PATH_SEPARATOR)) {
                file = file.substring(0, file.length() - PATH_SEPARATOR.length());
            }

            String finalFile = file;
            if (exclusionPatterns.stream().anyMatch(pattern -> pattern.matcher(finalFile).find())) {
                return false;
            }

            if (file.length() == normalizedRoot.length() - PATH_SEPARATOR.length()) {
                return true;
            } else {
                String subPath = file.substring(normalizedRoot.length());
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
        if (file.startsWith(normalizedRoot)
                || (file.length() == normalizedRoot.length() - PATH_SEPARATOR.length() && normalizedRoot.startsWith(file))) {
            return true;
        }
        return false;
    }

    @JsonIgnore
    public boolean includeDirectory(String path) {
        if (path.startsWith(normalizedRoot)
                || (path.length() == normalizedRoot.length() - PATH_SEPARATOR.length() && normalizedRoot.startsWith(path))) {
            if (path.endsWith(PATH_SEPARATOR)) {
                path = path.substring(0, path.length() - PATH_SEPARATOR.length());
            }

            if (path.length() == normalizedRoot.length() - PATH_SEPARATOR.length()) {
                return true;
            } else {
                String subPath = path.substring(normalizedRoot.length());
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
}
