package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

import static com.underscoreresearch.backup.io.implementation.UnderscoreBackupProvider.UB_TYPE;

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class BackupDestination {
    private String type;
    private String encryption;
    private String errorCorrection;
    private String endpointUri;
    private String principal;
    private String credential;
    private Integer maxConnections;
    private BackupTimespan maxRetention;
    private BackupTimespan minValidated;
    private Map<String, String> properties;
    private BackupLimits limits;

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
    public String getProperty(String name, String defaultValue) {
        if (properties != null) {
            String val = properties.get(name);
            if (val != null) {
                return val;
            }
        }
        return defaultValue;
    }

    @JsonIgnore
    public BackupDestination strippedDestination(String sourceId, String shareId) {
        return sourceShareDestination(sourceId, shareId).toBuilder()
                .credential(null).limits(null).principal(null).maxRetention(null).build();
    }

    @JsonIgnore
    public boolean isServiceDestination() {
        return type.equals(UB_TYPE);
    }

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
