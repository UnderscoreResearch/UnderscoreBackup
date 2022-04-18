package com.underscoreresearch.backup.cli.commands;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupLocation;

@CommandPlugin(value = "validate-blocks", description = "Validate that all used blocks for files exists",
        needPrivateKey = false, needConfiguration = true, readonlyRepository = false)
@Slf4j
public class ValidateBlocksCommand extends SimpleCommand {
    public void executeCommand() throws Exception {
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);

        repository.allFiles().forEach((file) -> {
            if (file.getLocations() != null) {
                List<BackupLocation> validCollections = file.getLocations().stream().filter(location -> {
                    for (BackupFilePart part : location.getParts()) {
                        try {
                            if (!validateHash(repository, part.getBlockHash())) {
                                return false;
                            }
                        } catch (IOException e) {
                            log.error("Failed to read block " + part.getBlockHash(), e);
                        }
                    }
                    return true;
                }).collect(Collectors.toList());

                try {
                    if (validCollections.size() != file.getLocations().size()) {
                        if (validCollections.size() == 0) {
                            repository.deleteFile(file);
                            log.error("Storage for {} does no longer exist", file.getPath());
                            repository.deleteFile(file);
                        } else {
                            log.warn("At least one location for {} no longer exists", file.getPath());
                            file.setLocations(validCollections);
                            repository.addFile(file);
                        }
                    }
                } catch (IOException e) {
                    log.error("Failed to delete missing file {}", file.getPath());
                }
            }
        });

        repository.flushLogging();
        manifestManager.shutdown();
        repository.close();
    }

    private boolean validateHash(MetadataRepository repository, String blockHash) throws IOException {
        BackupBlock block = repository.block(blockHash);
        if (block == null) {
            log.warn("Block hash {} does not exist", block);
            return false;
        }
        if (block.isSuperBlock()) {
            for (String hash : block.getHashes()) {
                validateHash(repository, hash);
            }
        } else {
            if (block.getStorage().stream()
                    .anyMatch(storage -> storage.getParts().stream()
                            .anyMatch(blockPart -> blockPart == null))) {
                log.warn("Block hash {} has missing parts", blockHash);
                return false;
            }
        }
        return true;
    }
}
