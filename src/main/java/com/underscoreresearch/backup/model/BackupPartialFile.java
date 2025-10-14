package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.file.MetadataRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.underscoreresearch.backup.utils.log.LogUtil.debug;
import static com.underscoreresearch.backup.utils.log.LogUtil.readableNumber;

/**
 * Represents a file that is partially backed up.
 * Contains information about a file and its parts that have been backed up so far,
 * with support for creating super blocks to optimize storage.
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(exclude = "parts")
@Slf4j
public class BackupPartialFile {
    /**
     * The maximum number of blocks in a super block.
     */
    public static final int SUPER_BLOCK_SIZE = 1000;
    
    /**
     * The minimum number of extra blocks before creating a super block.
     */
    public static final int MINIMUM_EXTRA_BLOCKS = 100;
    
    /**
     * The file being backed up.
     */
    private BackupFile file;
    
    /**
     * The parts of the file that have been backed up so far.
     */
    private List<PartialCompletedPath> parts;
    
    /**
     * The number of super blocks in the parts list.
     */
    private int superBlocks;

    /**
     * Constructor that initializes a BackupPartialFile with just a file.
     * 
     * @param file The file to back up
     */
    public BackupPartialFile(BackupFile file) {
        this.file = file;
    }

    /**
     * Adds a part to this partial file, potentially creating a super block if needed.
     * 
     * @param repository The metadata repository to use
     * @param part The part to add
     * @throws IOException If there's an error accessing the repository
     */
    @JsonIgnore
    public void addPart(MetadataRepository repository, PartialCompletedPath part) throws IOException {
        if (parts == null) {
            parts = new ArrayList<>();
        }
        parts.add(part);

        // There are places in the code that assumes that the last part will never be a super block.
        if (BackupBlock.isSuperBlock(part.getPart().getBlockHash())) {
            superBlocks = parts.size();
        } else if (parts.size() - superBlocks > SUPER_BLOCK_SIZE + MINIMUM_EXTRA_BLOCKS) {
            List<PartialCompletedPath> newList = Lists.newArrayList(parts.subList(0, superBlocks));
            int i = superBlocks;
            List<String> hashes = new ArrayList<>();
            List<Long> offsets = new ArrayList<>();
            long lastLocation = 0;
            while (hashes.size() < SUPER_BLOCK_SIZE) {
                PartialCompletedPath tp = parts.get(i);
                if (tp.getPart().getPartHash() != null) {
                    throw new RuntimeException("Can't mix multi file blocks into superblocks");
                }
                hashes.add(tp.getPart().getBlockHash());
                offsets.add(tp.getPart().getOffset());
                lastLocation = tp.getPosition();
                i++;
            }
            BackupBlock superBlock = BackupBlock.builder().created(Instant.now().toEpochMilli())
                    .hash(BackupBlock.createSuperBlockHash())
                    .hashes(hashes)
                    .offsets(offsets)
                    .storage(new ArrayList<>()).build();
            repository.addBlock(superBlock);
            newList.add(new PartialCompletedPath(lastLocation,
                    BackupFilePart.builder().blockHash(superBlock.getHash()).offset(offsets.get(0)).build()));
            superBlocks = newList.size();
            while (i < parts.size()) {
                newList.add(parts.get(i));
                i++;
            }
            debug(() -> log.debug("Created superblock \"{}\" and went from {} parts to {}", superBlock.getHash(),
                    readableNumber(parts.size()), readableNumber(newList.size())));
            parts = newList;
        }
    }

    /**
     * Represents a part of a file that has been backed up.
     * Contains information about the position in the file and the part itself.
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PartialCompletedPath {
        /**
         * The position in the file where this part starts.
         */
        private Long position;
        
        /**
         * The part itself.
         */
        private BackupFilePart part;
    }
}
