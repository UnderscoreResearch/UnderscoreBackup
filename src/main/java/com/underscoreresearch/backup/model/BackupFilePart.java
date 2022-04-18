package com.underscoreresearch.backup.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class BackupFilePart {
    private String blockHash;
    private String partHash;
    private Integer blockIndex;

    @JsonCreator
    @Builder
    public BackupFilePart(@JsonProperty("bh") String blockHash,
                          @JsonProperty("ph") String partHash,
                          @JsonProperty("bi") Integer blockIndex,
                          @JsonProperty("blockHash") String oldBlockHash,
                          @JsonProperty("partHash") String oldPartHash,
                          @JsonProperty("blockIndex") Integer oldBlockIndex) {
        this.blockHash = blockHash != null ? blockHash : oldBlockHash;
        this.partHash = partHash != null ? partHash : oldPartHash;
        this.blockIndex = blockIndex != null ? blockIndex : oldBlockIndex;
    }
}
