package com.underscoreresearch.backup.model;

import java.util.List;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupSet extends BackupFileSelection {
    private String id;
    private String schedule;
    private List<String> destinations;
    private BackupRetention retention;
    private Boolean continuous;

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
        super(calculateRoots(roots, root, filters), exclusions);

        this.id = id;
        this.retention = retention;
        this.schedule = schedule;
        this.destinations = destinations;
    }

    private static List<BackupSetRoot> calculateRoots(List<BackupSetRoot> roots, String root, List<BackupFilter> filters) {
        // This is just for backwards compatibility with old config files.
        if (root != null) {
            if (roots != null) {
                throw new IllegalArgumentException("Can't specify both roots and root");
            }
            return Lists.newArrayList(new BackupSetRoot(root, filters));
        } else {
            return roots;
        }
    }
}
