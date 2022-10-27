package com.underscoreresearch.backup.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.underscoreresearch.backup.encryption.EncryptionKey;

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class BackupBlockStorage {
    @JsonProperty("dest")
    private String destination;
    private String ec;
    @JsonProperty("enc")
    private String encryption;
    @JsonProperty("props")
    private Map<String, String> properties;
    private List<String> parts;
    @JsonProperty("c")
    private Long created;

    @JsonIgnore
    private Map<EncryptionKey, Map<String, String>> additionalStorageProperties;

    @JsonIgnore
    public synchronized Map<EncryptionKey, Map<String, String>> getAdditionalStorageProperties() {
        if (additionalStorageProperties == null) {
            additionalStorageProperties = new HashMap<>();
        }
        return additionalStorageProperties;
    }

    @JsonIgnore
    public boolean hasAdditionalStorageProperties() {
        return additionalStorageProperties != null && additionalStorageProperties.size() > 0;
    }

    @JsonIgnore
    public void addProperty(String key, String value) {
        if (properties == null)
            properties = new HashMap<>();
        properties.put(key, value);
    }
}
