package com.underscoreresearch.backup.model;

import static com.underscoreresearch.backup.io.implementation.UnderscoreBackupProvider.UB_TYPE;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

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
    private BackupTimespan maxRetention;
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
        return shareDestination(sourceId, shareId).toBuilder()
                .credential(null).limits(null).principal(null).maxRetention(null).build();
    }

    @JsonIgnore
    public boolean isServiceDestination() {
        return type.equals(UB_TYPE);
    }

    @JsonIgnore
    public BackupDestination shareDestination(String sourceId, String shareId) {
        String shareUri = endpointUri;

        // Strip off any share ID that might already be there.
        int firstRegion = shareUri.indexOf("/");
        if (firstRegion > 0) {
            shareUri = shareUri.substring(0, firstRegion);
        }

        // Add the share ID if it's not null.
        if (sourceId != null && shareId != null && isServiceDestination()) {
            shareUri = shareUri + "/" + sourceId + "/" + shareId;
        }
        return toBuilder().endpointUri(shareUri).build();
    }

}
