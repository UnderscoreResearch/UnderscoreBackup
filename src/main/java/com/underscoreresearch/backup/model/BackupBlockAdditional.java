package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents additional properties for a backup block.
 * Contains information about additional properties that can be applied to a block,
 * such as public key, hash, and storage-specific properties.
 */

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class BackupBlockAdditional {
    /**
     * The public key associated with this block.
     */
    private String publicKey;
    
    /**
     * The hash identifier for this block.
     */
    private String hash;
    
    /**
     * List of property maps for each storage location.
     */
    private List<Map<String, String>> properties;
    
    /**
     * Flag indicating whether this additional block is used.
     */
    private boolean used;
}
