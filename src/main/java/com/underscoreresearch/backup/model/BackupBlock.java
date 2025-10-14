package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.file.MetadataRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static com.underscoreresearch.backup.utils.log.LogUtil.debug;
import static com.underscoreresearch.backup.utils.log.LogUtil.readableNumber;

/**
 * Represents a block of data in the backup system.
 * A block can be a regular data block or a super block that references other blocks.
 * Contains information about the block's hash, creation time, format, and storage locations.
 */

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class BackupBlock {
    private static final String SUPERBLOCK_PREFIX = "S=";
    
    /**
     * The hash identifier for this block.
     */
    private String hash;
    
    /**
     * The timestamp when this block was created.
     */
    private long created;
    
    /**
     * The format of the data in this block.
     */
    private String format;

    /**
     * List of storage locations for this block.
     */
    private List<BackupBlockStorage> storage;
    
    /**
     * List of hashes for blocks referenced by this super block.
     */
    private List<String> hashes;
    
    /**
     * List of offsets for blocks referenced by this super block.
     */
    private List<Long> offsets;

    /**
     * Checks if a hash represents a super block.
     * 
     * @param hash The hash to check
     * @return True if the hash represents a super block, false otherwise
     */
    public static boolean isSuperBlock(String hash) {
        return hash.startsWith(SUPERBLOCK_PREFIX);
    }

    /**
     * Creates a new hash for a super block.
     * 
     * @return A new super block hash
     */
    public static String createSuperBlockHash() {
        return SUPERBLOCK_PREFIX + UUID.randomUUID();
    }

    /**
     * Expands a block into its constituent blocks if it's a super block.
     * 
     * @param blockHash The hash of the block to expand
     * @param repository The metadata repository to use
     * @return A list of blocks
     * @throws IOException If there's an error accessing the repository
     */
    public static List<BackupBlock> expandBlock(String blockHash, MetadataRepository repository) throws IOException {
        BackupBlock block = repository.block(blockHash);
        if (block.isSuperBlock()) {
            List<BackupBlock> blocks = new ArrayList<>();
            for (String hash : block.getHashes()) {
                BackupBlock childBlock = repository.block(hash);
                if (childBlock == null) {
                    throw new IOException("Block \"" + hash + "\" not found");
                }
                blocks.add(childBlock);
            }
            debug(() -> log.debug("Expanded super block \"{}\" to {} blocks", block.getHash(), readableNumber(blocks.size())));
            return blocks;
        }
        return Lists.newArrayList(block);
    }

    /**
     * Checks if this block is a super block.
     * 
     * @return True if this is a super block, false otherwise
     */
    @JsonIgnore
    public boolean isSuperBlock() {
        return isSuperBlock(hash);
    }

    /**
     * Creates a new block with additional properties.
     * 
     * @param blockAdditional The additional properties to add
     * @return A new block with the additional properties
     */
    public BackupBlock createAdditionalBlock(BackupBlockAdditional blockAdditional) {
        List<BackupBlockStorage> newStorages = new ArrayList<>();
        for (int i = 0; i < storage.size(); i++) {
            BackupBlockStorage cs = storage.get(i);
            BackupBlockStorage newStorage = cs.toBuilder()
                    .properties(cs.getProperties() != null ? new HashMap<>(cs.getProperties()) : null)
                    .build();
            if (blockAdditional.getProperties().get(i) != null) {
                if (newStorage.getProperties() == null) {
                    newStorage.setProperties(new HashMap<>());
                }
                newStorage.getProperties().putAll(blockAdditional.getProperties().get(i));
            }
            newStorages.add(newStorage);
        }
        return toBuilder().storage(newStorages).build();
    }
}
