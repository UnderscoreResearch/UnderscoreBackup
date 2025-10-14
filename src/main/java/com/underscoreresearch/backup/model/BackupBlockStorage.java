package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents storage information for a backup block.
 * Contains details about where and how a block is stored, including destination,
 * error correction, encryption, and other properties.
 */
@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class BackupBlockStorage {
    /**
     * The destination where the block is stored.
     */
    @JsonProperty("dest")
    private String destination;
    
    /**
     * The error correction method used for this block.
     */
    private String ec;
    
    /**
     * The encryption method used for this block.
     */
    @JsonProperty("enc")
    private String encryption;
    
    /**
     * Properties specific to this storage location.
     */
    @JsonProperty("props")
    private Map<String, String> properties;
    
    /**
     * List of parts that make up this block.
     */
    private List<String> parts;
    
    /**
     * Timestamp when this storage was created.
     */
    @JsonProperty("c")
    private Long created;
    
    /**
     * Timestamp when this storage was last validated.
     */
    @JsonProperty("v")
    private Long validated;

    /**
     * Additional storage properties for different identity keys.
     */
    @JsonIgnore
    private Map<IdentityKeys, Map<String, String>> additionalStorageProperties;

    /**
     * Gets the additional storage properties, initializing if necessary.
     * 
     * @return The additional storage properties map
     */
    @JsonIgnore
    public synchronized Map<IdentityKeys, Map<String, String>> getAdditionalStorageProperties() {
        if (additionalStorageProperties == null) {
            additionalStorageProperties = new HashMap<>();
        }
        return additionalStorageProperties;
    }

    /**
     * Checks if this storage has additional storage properties.
     * 
     * @return True if there are additional storage properties, false otherwise
     */
    @JsonIgnore
    public boolean hasAdditionalStorageProperties() {
        return additionalStorageProperties != null && !additionalStorageProperties.isEmpty();
    }

    /**
     * Adds a property to this storage.
     * 
     * @param key The property key
     * @param value The property value
     */
    @JsonIgnore
    public void addProperty(String key, String value) {
        if (properties == null)
            properties = new HashMap<>();
        properties.put(key, value);
    }
}
