package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a part of a file in the backup system.
 * Contains information about a part of a file, including its block hash, part hash,
 * block index, and offset within the file.
 */

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class BackupFilePart {
    /**
     * The hash of the block containing this part.
     */
    @JsonProperty("bh")
    private String blockHash;
    
    /**
     * The hash of this part.
     */
    @JsonProperty("ph")
    private String partHash;
    
    /**
     * The index of this part within the block.
     */
    @JsonProperty("bi")
    private Integer blockIndex;
    
    /**
     * The offset of this part within the file.
     */
    @JsonProperty("o")
    private Long offset;

    /**
     * Legacy getter for blockHash.
     * 
     * @return Always returns null
     * @deprecated Use getBlockHash() instead
     */
    @JsonProperty("blockHash")
    @Deprecated
    public String getLegacyBlockHash() {
        return null;
    }

    /**
     * Legacy setter for blockHash.
     * 
     * @param blockHash The block hash to set
     * @deprecated Use setBlockHash() instead
     */
    @JsonProperty("blockHash")
    @Deprecated
    public void setLegacyBlockHash(String blockHash) {
        this.blockHash = blockHash;
    }

    /**
     * Legacy getter for partHash.
     * 
     * @return Always returns null
     * @deprecated Use getPartHash() instead
     */
    @JsonProperty("partHash")
    @Deprecated
    public String getLegacyPartHash() {
        return null;
    }

    /**
     * Legacy setter for partHash.
     * 
     * @param partHash The part hash to set
     * @deprecated Use setPartHash() instead
     */
    @JsonProperty("partHash")
    @Deprecated
    public void setLegacyPartHash(String partHash) {
        this.partHash = partHash;
    }

    /**
     * Legacy getter for blockIndex.
     * 
     * @return Always returns null
     * @deprecated Use getBlockIndex() instead
     */
    @JsonProperty("blockIndex")
    @Deprecated
    public Integer getLegacyBlockIndex() {
        return null;
    }

    /**
     * Legacy setter for blockIndex.
     * 
     * @param blockIndex The block index to set
     * @deprecated Use setBlockIndex() instead
     */
    @JsonProperty("blockIndex")
    @Deprecated
    public void setLegacyBlockIndex(Integer blockIndex) {
        this.blockIndex = blockIndex;
    }
}
