package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Represents an activated share in the backup system.
 * Contains information about a share that has been activated for use,
 * including the share details, destinations used, and encryption status.
 */

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class BackupActivatedShare {
    /**
     * The share that has been activated.
     */
    private BackupShare share;
    
    /**
     * Set of destination IDs that are used by this share.
     */
    private Set<String> usedDestinations;
    
    /**
     * Flag indicating whether the encryption has been updated.
     */
    private boolean updatedEncryption;
}
