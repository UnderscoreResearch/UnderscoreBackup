package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;

/**
 * Represents a share of backup data with another user.
 * Contains information about what data is shared, with whom, and where it is stored.
 */

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class BackupShare {
    /**
     * The name of this share.
     */
    private String name;
    
    /**
     * The email address of the user to share with.
     */
    private String targetEmail;
    
    /**
     * The destination where the shared data is stored.
     */
    private BackupDestination destination;
    
    /**
     * The specification of what files are included in this share.
     */
    private BackupFileSpecification contents;

    /**
     * Creates an activated share from this share.
     * 
     * @param sourceId The source ID to include in the destination
     * @param shareId The share ID to include in the destination
     * @return The activated share
     */
    @JsonIgnore
    public BackupActivatedShare activatedShare(String sourceId, String shareId) {
        return BackupActivatedShare.builder()
                .share(toBuilder().destination(getDestination().sourceShareDestination(sourceId, shareId)).build())
                .usedDestinations(new HashSet<>())
                .build();
    }
}
