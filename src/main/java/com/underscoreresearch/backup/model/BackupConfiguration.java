package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.Maps;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents the configuration for the backup system.
 * Contains information about backup sets, destinations, manifest, properties, limits, and shares.
 */

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class BackupConfiguration {
    /**
     * List of backup sets defined in this configuration.
     */
    private List<BackupSet> sets;
    
    /**
     * Map of destination IDs to backup destinations.
     */
    private Map<String, BackupDestination> destinations;
    
    /**
     * The manifest configuration.
     */
    private BackupManifest manifest;
    
    /**
     * Map of property names to values.
     */
    private Map<String, String> properties;
    
    /**
     * Global limits for the backup system.
     */
    private BackupGlobalLimits limits;
    
    /**
     * Retention policy for missing files.
     */
    private BackupRetention missingRetention;
    
    /**
     * Map of additional source IDs to backup destinations.
     */
    private Map<String, BackupDestination> additionalSources;
    
    /**
     * Map of share IDs to backup shares.
     */
    private Map<String, BackupShare> shares;

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
     * Gets a long property value.
     * 
     * @param name The property name
     * @param defaultValue The default value to return if the property is not found
     * @return The property value as a long
     */
    @JsonIgnore
    public long getProperty(String name, long defaultValue) {
        if (properties != null) {
            String val = properties.get(name);
            if (val != null) {
                return Long.parseLong(val);
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
     * Creates a stripped copy of this configuration.
     * The stripped copy has sensitive information removed from destinations and shares.
     * 
     * @return A stripped copy of this configuration
     */
    public BackupConfiguration strippedCopy() {
        return toBuilder()
                .destinations(destinations.entrySet().stream()
                        .map(e -> Maps.immutableEntry(e.getKey(), e.getValue().strippedDestination(null, null)))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .shares(shares != null ? shares.entrySet().stream()
                        .map(e -> Maps.immutableEntry(e.getKey(),
                                e.getValue().toBuilder().destination(
                                        e.getValue().getDestination().strippedDestination(null, null)).build()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)) : null)
                .additionalSources(additionalSources != null ? additionalSources.entrySet().stream()
                        .map(e -> Maps.immutableEntry(e.getKey(), e.getValue().strippedDestination(null, null)))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)) : null)
                .build();
    }
}
