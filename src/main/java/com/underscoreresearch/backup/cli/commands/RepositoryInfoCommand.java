package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.utils.LogUtil.readableNumber;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.model.BackupBlockStorage;

@CommandPlugin(value = "repository-info", description = "Show info about contents of repository",
        needPrivateKey = false, needConfiguration = true)
public class RepositoryInfoCommand extends SimpleCommand {
    public void executeCommand() throws Exception {
        AtomicLong totalFiles = new AtomicLong();
        AtomicLong totalVersions = new AtomicLong();
        AtomicLong totalOriginalSize = new AtomicLong();
        AtomicLong totalOriginalCurrentSize = new AtomicLong();
        AtomicLong totalBlocks = new AtomicLong();
        AtomicLong totalParts = new AtomicLong();
        AtomicReference<String> lastPath = new AtomicReference<>("");

        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);

        try (CloseableLock ignored = repository.acquireLock()) {
            repository.allFiles(false).forEachOrdered((file) -> {
                if (!file.getPath().equals(lastPath.get())) {
                    lastPath.set(file.getPath());
                    totalFiles.incrementAndGet();
                    totalOriginalCurrentSize.addAndGet(file.getLength());
                }
                totalVersions.incrementAndGet();
                totalOriginalSize.addAndGet(file.getLength());
            });

            System.out.println("Files: " + readableNumber(totalFiles.get()));
            System.out.println("Last version file size: " + readableSize(totalOriginalCurrentSize.get()));
            System.out.println("File versions: " + readableNumber(totalVersions.get()));
            System.out.println("Total original size: " + readableSize(totalOriginalSize.get()));

            repository.allBlocks().forEach(block -> {
                totalBlocks.incrementAndGet();
                for (BackupBlockStorage storage : block.getStorage())
                    totalParts.addAndGet(storage.getParts().size());
            });
        }

        System.out.println("Total blocks: " + readableNumber(totalBlocks.get()));
        System.out.println("Total block parts: " + readableNumber(totalParts.get()));
    }
}
