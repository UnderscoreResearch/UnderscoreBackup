package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.util.concurrent.atomic.AtomicLong;

import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.InstanceFactory;
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
        String[] lastPath = new String[1];
        long[] lastLength = new long[1];

        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);

        repository.allFiles().forEach((file) -> {
            if (!file.getPath().equals(lastPath[0])) {
                lastPath[0] = file.getPath();
                totalFiles.incrementAndGet();
                totalOriginalCurrentSize.addAndGet(lastLength[0]);
            }
            lastLength[0] = file.getLength();
            totalVersions.incrementAndGet();
            totalOriginalSize.addAndGet(file.getLength());
        });
        totalOriginalCurrentSize.addAndGet(lastLength[0]);

        System.out.println("Files: " + totalFiles.get());
        System.out.println("Last version file size: " + readableSize(totalOriginalCurrentSize.get()));
        System.out.println("File versions: " + totalVersions.get());
        System.out.println("Total original size: " + readableSize(totalOriginalSize.get()));

        repository.allBlocks().forEach(block -> {
            totalBlocks.incrementAndGet();
            for (BackupBlockStorage storage : block.getStorage())
                totalParts.addAndGet(storage.getParts().size());
        });

        System.out.println("Total blocks: " + totalBlocks.get());
        System.out.println("Total block parts: " + totalParts.get());
    }
}
