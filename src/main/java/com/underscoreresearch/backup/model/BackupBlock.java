package com.underscoreresearch.backup.model;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class BackupBlock {
    private static final String SUPERBLOCK_PREFIX = "S=";
    private String hash;
    private long created;
    private String format;

    private List<BackupBlockStorage> storage;
    private List<String> hashes;

    @JsonIgnore
    public boolean isSuperBlock() {
        return isSuperBlock(hash);
    }

    public static boolean isSuperBlock(String hash) {
        return hash.startsWith(SUPERBLOCK_PREFIX);
    }

    public static String createSuperBlockHash() {
        return SUPERBLOCK_PREFIX + UUID.randomUUID();
    }
}
