package com.underscoreresearch.backup.model;

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
public class BackupFilePart {
    @JsonProperty("bh")
    private String blockHash;
    @JsonProperty("ph")
    private String partHash;
    @JsonProperty("bi")
    private Integer blockIndex;
    @JsonProperty("o")
    private Long offset;

    @JsonProperty("blockHash")
    @Deprecated
    public String getLegacyBlockHash() {
        return null;
    }

    // Everything below here is for backwards compatibility of JSON format
    @JsonProperty("blockHash")
    @Deprecated
    public void setLegacyBlockHash(String blockHash) {
        this.blockHash = blockHash;
    }

    @JsonProperty("partHash")
    @Deprecated
    public String getLegacyPartHash() {
        return null;
    }

    @JsonProperty("partHash")
    @Deprecated
    public void setLegacyPartHash(String partHash) {
        this.partHash = partHash;
    }

    @JsonProperty("blockIndex")
    @Deprecated
    public Integer getLegacyBlockIndex() {
        return null;
    }

    @JsonProperty("blockIndex")
    @Deprecated
    public void setLegacyBlockIndex(Integer blockIndex) {
        this.blockIndex = blockIndex;
    }
}
