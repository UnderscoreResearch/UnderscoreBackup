package com.underscoreresearch.backup.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.Maps;

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class BackupConfiguration {
    private List<BackupSet> sets;
    private Map<String, BackupDestination> destinations;
    private BackupManifest manifest;
    private Map<String, String> properties;
    private BackupGlobalLimits limits;
    private BackupRetention missingRetention;
    private Map<String, BackupDestination> additionalSources;
    private Map<String, BackupShare> shares;

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
