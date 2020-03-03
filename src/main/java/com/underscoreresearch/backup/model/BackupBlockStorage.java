package com.underscoreresearch.backup.model;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@Builder
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
}
