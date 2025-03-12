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

import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.readableNumber;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(exclude = "parts")
@Slf4j
public class BackupPartialFile {
    public static final int SUPER_BLOCK_SIZE = 1000;
    public static final int MINIMUM_EXTRA_BLOCKS = 100;
    private BackupFile file;
    private List<PartialCompletedPath> parts;
    private int superBlocks;

    public BackupPartialFile(BackupFile file) {
        this.file = file;
    }

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

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PartialCompletedPath {
        private Long position;
        private BackupFilePart part;
    }
}
