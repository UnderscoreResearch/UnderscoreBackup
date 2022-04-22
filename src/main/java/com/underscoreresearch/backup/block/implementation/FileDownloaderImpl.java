package com.underscoreresearch.backup.block.implementation;

import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.block.BlockFormatFactory;
import com.underscoreresearch.backup.block.FileBlockExtractor;
import com.underscoreresearch.backup.block.FileDownloader;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupLocation;
import com.underscoreresearch.backup.utils.StatusLine;
import com.underscoreresearch.backup.utils.StatusLogger;

@Slf4j
public class FileDownloaderImpl implements FileDownloader, StatusLogger {
    @Data
    private static class Progress {
        private long completed;
        private long total;

        public Progress(long total) {
            this.total = total;
        }
    }

    private final MetadataRepository repository;
    private final FileSystemAccess fileSystemAccess;
    private AtomicBoolean shutdown = new AtomicBoolean();
    private TreeMap<String, Progress> activeFiles = new TreeMap<>();

    public FileDownloaderImpl(MetadataRepository repository,
                              FileSystemAccess fileSystemAccess) {
        this.repository = repository;
        this.fileSystemAccess = fileSystemAccess;
    }

    public static boolean isNullFile(String file) {
        return (file != null && (file.equals("-") || file.equals("=")));
    }

    @Override
    public void downloadFile(BackupFile source, String destinationFile) throws IOException {
        Progress progress = new Progress(source.getLength());
        synchronized (activeFiles) {
            activeFiles.put(source.getPath(), progress);
        }

        try {
            for (int i = 0; true; i++) {
                try {
                    if (source.getLength() == 0) {
                        if (!isNullFile(destinationFile))
                            fileSystemAccess.truncate(destinationFile, 0);
                    } else {
                        BackupLocation location = source.getLocations().get(i);
                        long offset = 0;

                        FileInputStream originalStream = null;
                        if (destinationFile.equals("=")) {
                            File originalFile = new File(PathNormalizer.physicalPath(source.getPath()));
                            if (!originalFile.isFile()) {
                                log.warn("File missing locally {}", originalFile.getAbsolutePath());
                                originalFile = null;
                            } else if (!originalFile.canRead()) {
                                log.warn("Can't read file {} to compare", originalFile.getAbsolutePath());
                                originalFile = null;
                            } else if (originalFile.length() != source.getLength()) {
                                log.warn("File size does not match {} ({} in backup, {} locally})", originalFile.getAbsolutePath(),
                                        readableSize(source.getLength()), readableSize(originalFile.length()));
                                originalFile = null;
                            }

                            if (originalFile != null) {
                                try {
                                    originalStream = new FileInputStream(originalFile);
                                } catch (IOException exc) {
                                    log.warn("Can't open file {} to compare", originalFile.getAbsolutePath());
                                }
                            }
                        }

                        try {
                            for (BackupFilePart part : location.getParts()) {
                                List<BackupBlock> blocks = expandBlocks(part.getBlockHash());
                                for (BackupBlock block : blocks) {
                                    if (block == null) {
                                        throw new IOException(String.format("File referenced block {} that doesn't exist",
                                                part.getBlockHash()));
                                    }
                                    FileBlockExtractor extractor = BlockFormatFactory.getExtractor(block.getFormat());
                                    try {
                                        byte[] fileData = extractor.extractPart(part);
                                        if (fileData == null) {
                                            throw new IOException("Failed to extra data for part of block " + part.getBlockHash());
                                        }

                                        if (!isNullFile(destinationFile)) {
                                            fileSystemAccess.writeData(destinationFile, fileData, offset, fileData.length);
                                        } else if (destinationFile.equals("=")) {
                                            for (int originalOffset = 0; originalStream != null && originalOffset < fileData.length; originalOffset += 8192) {
                                                byte[] original = new byte[8192];
                                                int length = Math.min(8192, fileData.length - originalOffset);
                                                originalStream.read(original, 0, length);
                                                for (int j = 0; j < length; j++)
                                                    if (original[j] != fileData[j + originalOffset]) {
                                                        log.warn("File {} does not match locally at location {}",
                                                                PathNormalizer.physicalPath(source.getPath()), offset + j);
                                                        originalStream.close();
                                                        originalStream = null;
                                                        break;
                                                    }
                                            }
                                        }

                                        offset += fileData.length;
                                        progress.setCompleted(offset);
                                    } catch (Exception exc) {
                                        throw new IOException("Failed to download " + source.getPath()
                                                + " because missing or corrupt block " + block.getHash(), exc);
                                    }
                                }
                            }
                        } finally {
                            if (originalStream != null)
                                originalStream.close();
                        }

                        if (!isNullFile(destinationFile))
                            fileSystemAccess.truncate(destinationFile, offset);

                        if (offset != source.getLength()) {
                            throw new IOException(String.format("Expected file %s to be of size %s but was actually %s",
                                    source.getPath(), source.getLength(), offset));
                        }
                    }
                    break;
                } catch (IOException exc) {
                    if (source.getLocations() == null || i == source.getLocations().size() - 1 || shutdown.get()) {
                        throw exc;
                    }
                }
            }
        } finally {
            synchronized (activeFiles) {
                activeFiles.remove(source.getPath());
            }
        }
    }

    private List<BackupBlock> expandBlocks(String blockHash) throws IOException {
        BackupBlock block = repository.block(blockHash);
        if (block.isSuperBlock()) {
            List<BackupBlock> blocks = new ArrayList<>();
            for (String hash : block.getHashes()) {
                blocks.add(repository.block(hash));
            }
            debug(() -> log.debug("Expanded suprt block {} to {} blocks", block.getHash(), blocks.size()));
            return blocks;
        }
        return Lists.newArrayList(block);
    }

    @Override
    public void shutdown() {
        shutdown.set(true);
    }

    @Override
    public void resetStatus() {
        synchronized (activeFiles) {
            activeFiles.clear();
        }
    }

    @Override
    public List<StatusLine> status() {
        synchronized (activeFiles) {
            return activeFiles.entrySet().stream().map(entry -> new StatusLine(getClass(),
                    "DOWNLOADED_ACTIVE_" + entry.getKey(),
                    "Currently downloading " + entry.getKey(),
                    entry.getValue().getCompleted(),
                    readableSize(entry.getValue().getCompleted()) + " / "
                            + readableSize(entry.getValue().getTotal()))).collect(Collectors.toList());
        }
    }
}
