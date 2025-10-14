package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

import static com.underscoreresearch.backup.io.implementation.UnderscoreBackupProvider.UB_TYPE;

/**
 * Represents a destination for backup data.
 * Contains information about the storage location, encryption, error correction,
 * credentials, and limits for a backup destination.
 */

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class BackupDestination {
    /**
     * The type of destination (e.g., S3, local, Dropbox).
     */
    private String type;
    
    /**
     * The encryption method to use for this destination.
     */
    private String encryption;
    
    /**
     * The error correction method to use for this destination.
     */
    private String errorCorrection;
    
    /**
     * The URI for the endpoint of this destination.
     */
    private String endpointUri;
    
    /**
     * The principal (username, access key, etc.) for authentication.
     */
    private String principal;
    
    /**
     * The credential (password, secret key, etc.) for authentication.
     */
    private String credential;
    
    /**
     * The maximum number of connections to use for this destination.
     */
    private Integer maxConnections;
    
    /**
     * The maximum retention period for data in this destination.
     */
    private BackupTimespan maxRetention;
    
    /**
     * The minimum time between validations for data in this destination.
     */
    private BackupTimespan minValidated;
    
    /**
     * Additional properties for this destination.
     */
    private Map<String, String> properties;
    
    /**
     * Limits for this destination.
     */
    private BackupLimits limits;

    /**
     * Gets an integer property value.
     * 
     * @param name The property name
     * @param defaultValue The default value to return if the property is not found
     * @return The property value as an integer
     */
    @JsonIgnore
    public int getProperty(String name, int defaultValue) {
        if (properties != null) {
            String val = properties.get(name);
            if (val != null) {
                return Integer.parseInt(val);
            }
        }
        return defaultValue;
    }

    /**
     * Gets a double property value.
     * 
     * @param name The property name
     * @param defaultValue The default value to return if the property is not found
     * @return The property value as a double
     */
    @JsonIgnore
    public double getProperty(String name, double defaultValue) {
        if (properties != null) {
            String val = properties.get(name);
            if (val != null) {
                return Double.parseDouble(val);
            }
        }
        return defaultValue;
    }

    /**
     * Gets a string property value.
     * 
     * @param name The property name
     * @param defaultValue The default value to return if the property is not found
     * @return The property value as a string
     */
    @JsonIgnore
    public String getProperty(String name, String defaultValue) {
        if (properties != null) {
            String val = properties.get(name);
            if (val != null) {
                return val;
            }
        }
        return defaultValue;
    }

    /**
     * Creates a stripped version of this destination with sensitive information removed.
     * 
     * @param sourceId The source ID to include in the endpoint URI
     * @param shareId The share ID to include in the endpoint URI
     * @return A stripped version of this destination
     */
    @JsonIgnore
    public BackupDestination strippedDestination(String sourceId, String shareId) {
        return sourceShareDestination(sourceId, shareId).toBuilder()
                .credential(null).limits(null).principal(null).maxRetention(null).build();
    }

    /**
     * Checks if this destination is a service destination.
     * 
     * @return True if this is a service destination, false otherwise
     */
    @JsonIgnore
    public boolean isServiceDestination() {
        return type.equals(UB_TYPE);
    }

    /**
     * Creates a version of this destination with source and share IDs included in the endpoint URI.
     * 
     * @param sourceId The source ID to include
     * @param shareId The share ID to include
     * @return A version of this destination with source and share IDs included
     */
    @JsonIgnore
    public BackupDestination sourceShareDestination(String sourceId, String shareId) {
        if (isServiceDestination()) {
            String shareUri = endpointUri;

            // Strip off any share ID that might already be there.
            int firstRegion = shareUri.indexOf("/");
            if (firstRegion > 0) {
                shareUri = shareUri.substring(0, firstRegion);
            }

            // Add the source and share ID if it's not null.
            if (sourceId != null) {
                if (shareId != null) {
                    shareUri = shareUri + "/" + sourceId + "/" + shareId;
                } else {
                    shareUri = shareUri + "/" + sourceId;
                }
            }
            return toBuilder().endpointUri(shareUri).build();
        }
        return this;
    }
}
