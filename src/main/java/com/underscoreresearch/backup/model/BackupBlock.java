package com.underscoreresearch.backup.model;

import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.readableNumber;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.file.MetadataRepository;

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class BackupBlock {
    private static final String SUPERBLOCK_PREFIX = "S=";
    private String hash;
    private long created;
    private String format;

    private List<BackupBlockStorage> storage;
    private List<String> hashes;
    private List<Long> offsets;

    public static boolean isSuperBlock(String hash) {
        return hash.startsWith(SUPERBLOCK_PREFIX);
    }

    public static String createSuperBlockHash() {
        return SUPERBLOCK_PREFIX + UUID.randomUUID();
    }

    public static List<BackupBlock> expandBlock(String blockHash, MetadataRepository repository) throws IOException {
        BackupBlock block = repository.block(blockHash);
        if (block.isSuperBlock()) {
            List<BackupBlock> blocks = new ArrayList<>();
            for (String hash : block.getHashes()) {
                BackupBlock childBlock = repository.block(hash);
                if (childBlock == null) {
                    throw new IOException("Block " + hash + " not found");
                }
                blocks.add(childBlock);
            }
            debug(() -> log.debug("Expanded super block {} to {} blocks", block.getHash(), readableNumber(blocks.size())));
            return blocks;
        }
        return Lists.newArrayList(block);
    }

    @JsonIgnore
    public boolean isSuperBlock() {
        return isSuperBlock(hash);
    }

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
