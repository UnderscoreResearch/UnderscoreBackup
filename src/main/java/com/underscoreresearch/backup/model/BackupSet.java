package com.underscoreresearch.backup.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import com.google.common.collect.Lists;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupSet {
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @JsonIgnore
    private List<Pattern> exclusionPatterns;
    @JsonIgnore
    private List<Pattern> exclusionDirectoryPatterns;

    private String id;
    private List<String> exclusions;
    private String schedule;
    private List<String> destinations;
    private BackupRetention retention;
    private List<BackupSetRoot> roots;

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

    @JsonCreator
    @Builder
    public BackupSet(@JsonProperty("id") String id,
                     @JsonProperty("roots") List<BackupSetRoot> roots,
                     @JsonProperty("exclusions") List<String> exclusions,
                     @JsonProperty("schedule") String schedule,
                     @JsonProperty("destinations") List<String> destinations,
                     @JsonProperty("retention") BackupRetention retention,
                     @JsonProperty("root") String root,
                     @JsonProperty("filters") List<BackupFilter> filters) {
        setExclusions(exclusions);

        // This is just for backwards compatibility with old config files.
        if (root != null) {
            if (roots != null) {
                throw new IllegalArgumentException("Can't specify both roots and root");
            }
            this.roots = Lists.newArrayList(new BackupSetRoot(root, filters));
        } else {
            this.roots = roots;
        }

        this.id = id;
        this.retention = retention;
        this.schedule = schedule;
        this.destinations = destinations;
    }

    @JsonIgnore
    boolean checkExcluded(String finalFile) {
        if (exclusionPatterns.stream().anyMatch(pattern -> pattern.matcher(finalFile).find())) {
            return true;
        }
        return false;
    }

    @JsonIgnore
    private boolean checkExcludedDirectory(String finalDirectory) {
        if (exclusionDirectoryPatterns.stream().anyMatch(pattern -> pattern.matcher(finalDirectory).find())) {
            return true;
        }
        return false;
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
    public String getAllRoots() {
        return String.join(", ", roots.stream().map(t -> t.getPath()).collect(Collectors.toList()));
    }
}
