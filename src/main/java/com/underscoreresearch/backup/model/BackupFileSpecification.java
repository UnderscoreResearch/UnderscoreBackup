package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a file specification for a backup.
 * Extends BackupFileSelection to provide a more specific file selection criteria.
 */

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupFileSpecification extends BackupFileSelection {

    /**
     * Constructor that initializes a BackupFileSpecification with roots and exclusions.
     * 
     * @param roots The list of root directories to include
     * @param exclusions The list of exclusion patterns
     */
    @JsonCreator
    @Builder
    public BackupFileSpecification(@JsonProperty("roots") List<BackupSetRoot> roots,
                                   @JsonProperty("exclusions") List<String> exclusions) {
        super(roots, exclusions);
    }
}
