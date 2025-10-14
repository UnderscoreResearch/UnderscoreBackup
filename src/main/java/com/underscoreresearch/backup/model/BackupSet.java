package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a set of files to back up.
 * Contains information about what files to back up, when to back them up,
 * where to store them, and how long to keep them.
 */

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupSet extends BackupFileSelection {
    /**
     * The unique identifier for this backup set.
     */
    private String id;
    
    /**
     * The schedule for when to run this backup set.
     */
    private String schedule;
    
    /**
     * List of destination IDs where to store this backup set.
     */
    private List<String> destinations;
    
    /**
     * Retention policy for this backup set.
     */
    private BackupRetention retention;
    
    /**
     * Whether this backup set should run continuously.
     */
    private Boolean continuous;

    /**
     * Constructor that initializes a BackupSet with various properties.
     * 
     * @param id The unique identifier for this backup set
     * @param roots The list of root directories to include
     * @param exclusions The list of exclusion patterns
     * @param schedule The schedule for when to run this backup set
     * @param destinations The list of destination IDs where to store this backup set
     * @param retention The retention policy for this backup set
     * @param root Legacy parameter for a single root directory
     * @param filters Legacy parameter for filters to apply to the root directory
     */
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

    /**
     * Calculates the roots for this backup set.
     * Handles backward compatibility with old config files that used a single root.
     * 
     * @param roots The list of root directories
     * @param root Legacy parameter for a single root directory
     * @param filters Legacy parameter for filters to apply to the root directory
     * @return The list of root directories
     */
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
