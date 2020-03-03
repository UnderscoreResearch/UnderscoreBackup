package com.underscoreresearch.backup.block.implementation;

import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.block.BlockFormatFactory;
import com.underscoreresearch.backup.block.FileBlockExtractor;
import com.underscoreresearch.backup.block.FileDownloader;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
import com.underscoreresearch.backup.errorcorrection.ErrorCorrector;
import com.underscoreresearch.backup.errorcorrection.ErrorCorrectorFactory;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupLocation;
import com.underscoreresearch.backup.utils.StatusLogger;

@RequiredArgsConstructor
@Slf4j
public class FileDownloaderImpl implements FileDownloader, StatusLogger {
    public static final String NULL_FILE = "-";
    private final MetadataRepository repository;
    private final FileSystemAccess fileSystemAccess;
    private final RateLimitController rateLimitController;
    private final BackupConfiguration configuration;
    private AtomicBoolean shutdown = new AtomicBoolean();
    private AtomicLong totalSize = new AtomicLong();
    private AtomicLong totalCount = new AtomicLong();

    private LoadingCache<BackupBlock, byte[]> blockData = CacheBuilder.newBuilder().maximumSize(2)
            .build(new CacheLoader<BackupBlock, byte[]>() {
                @Override
                public byte[] load(BackupBlock key) throws Exception {
                    return downloadBlock(key);
                }
            });

    @Override
    public void downloadFile(BackupFile source, String destinationFile) throws IOException {
        for (int i = 0; true; i++) {
            try {
                BackupLocation location = source.getLocations().get(0);
                long offset = 0;
                for (BackupFilePart part : location.getParts()) {
                    BackupBlock block = repository.block(part.getBlockHash());
                    FileBlockExtractor extractor = BlockFormatFactory.getExtractor(block.getFormat());
                    try {
                        byte[] downloadedBlock;

                        if (extractor.shouldCache())
                            downloadedBlock = blockData.get(block);
                        else
                            downloadedBlock = downloadBlock(block);

                        byte[] fileData = extractor.extractPart(part, downloadedBlock);
                        if (fileData == null) {
                            throw new IOException("Failed to extra data for part of block " + part.getBlockHash());
                        }

                        if (!destinationFile.equals(NULL_FILE))
                            fileSystemAccess.writeData(destinationFile, fileData, offset, fileData.length);
                        offset += fileData.length;
                    } catch (Exception exc) {
                        throw new IOException("Failed to download " + source.getPath()
                                + " because missing or corrupt block " + block.getHash(), exc);
                    }
                }
                if (!destinationFile.equals(NULL_FILE))
                    fileSystemAccess.truncate(destinationFile, offset);
                break;
            } catch (Exception exc) {
                if (i == source.getLocations().size() - 1 || shutdown.get()) {
                    if (exc instanceof IOException)
                        throw (IOException) exc;
                    else
                        throw new IOException("Failed to download " + source.getPath(), exc);
                }
            }
        }
    }

    @Override
    public void shutdown() {
        shutdown.set(true);
    }

    private byte[] downloadBlock(BackupBlock block) throws IOException {
        for (int storageIndex = 0; storageIndex < block.getStorage().size(); storageIndex++) {
            BackupBlockStorage storage = block.getStorage().get(storageIndex);
            try {
                byte[][] blockParts = new byte[storage.getParts().size()][];

                BackupDestination destination = configuration.getDestinations().get(storage.getDestination());
                IOProvider provider = IOProviderFactory.getProvider(destination);
                int completedParts = 0;
                ErrorCorrector corrector = ErrorCorrectorFactory.getCorrector(destination.getErrorCorrection());
                int neededParts = corrector.getMinimumSufficientParts(storage);
                byte[] errorCorrected = null;

                for (int i = 0; i < storage.getParts().size(); i++) {
                    if (shutdown.get()) {
                        throw new IOException("Shutting down");
                    }
                    try {
                        byte[] data = provider.download(storage.getParts().get(i));
                        totalCount.incrementAndGet();
                        totalSize.addAndGet(data.length);
                        blockParts[i] = data;
                        rateLimitController.acquireDownloadPermits(destination, data.length);
                        completedParts++;
                    } catch (IOException exc) {
                        log.error("Failed to download " + storage.getParts().get(i) + " from " + storage.getDestination());
                    }

                    if (completedParts >= neededParts) {
                        try {
                            errorCorrected = corrector.decodeErrorCorrection(storage, Lists.newArrayList(blockParts));
                            break;
                        } catch (Exception e) {
                            if (i == storage.getParts().size() - 1) {
                                throw new IOException("Failed to error correct " + block.getHash(), e);
                            }
                        }
                    }
                }

                return EncryptorFactory.decodeBlock(storage, errorCorrected);
            } catch (Exception exc) {
                if (storageIndex == block.getStorage().size() - 1 || shutdown.get()) {
                    throw new IOException("Failed to download block " + block.getHash() + " was unreadable", exc);
                }
            }
        }
        throw new IOException("No storage available for block");
    }

    @Override
    public void logStatus() {
        if (totalCount.get() > 0) {
            debug(() -> log.debug("Downloaded {} objects of total size {}", totalCount.get(),
                    readableSize(totalSize.get())));
        }
    }
}
