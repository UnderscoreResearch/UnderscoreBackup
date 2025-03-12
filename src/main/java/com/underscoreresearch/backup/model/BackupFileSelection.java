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

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupFileSelection {
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @JsonIgnore
    private List<Pattern> exclusionPatterns;
    @JsonIgnore
    private List<Pattern> exclusionDirectoryPatterns;

    private List<String> exclusions;
    private List<BackupSetRoot> roots;

    public BackupFileSelection(@JsonProperty("roots") List<BackupSetRoot> roots,
                               @JsonProperty("exclusions") List<String> exclusions) {
        setExclusions(exclusions);

        this.roots = roots;
    }

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

    @JsonIgnore
    boolean checkExcluded(String finalFile) {
        return exclusionPatterns.stream().anyMatch(pattern -> pattern.matcher(finalFile).find());
    }

    @JsonIgnore
    private boolean checkExcludedDirectory(String finalDirectory) {
        return exclusionDirectoryPatterns.stream().anyMatch(pattern -> pattern.matcher(finalDirectory).find());
    }

    @JsonIgnore
    public boolean includeFile(String file) {
        for (BackupSetRoot root : roots) {
            if (root.includeFile(file, this))
                return true;
        }
        return false;
    }


    @JsonIgnore
    public boolean inRoot(String file) {
        for (BackupSetRoot root : roots) {
            if (root.inRoot(file))
                return true;
        }
        return false;
    }


    @JsonIgnore
    public boolean includeDirectory(String path) {
        for (BackupSetRoot root : roots) {
            if (root.includeDirectory(path)) {
                return !checkExcludedDirectory(path);
            }
        }
        return false;
    }

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

    @JsonIgnore
    public String getAllRoots() {
        return "\"" + roots.stream().map(BackupSetRoot::getPath).collect(Collectors.joining("\", \"")) + "\"";
    }
}
