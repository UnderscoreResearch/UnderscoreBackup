package com.underscoreresearch.backup.block.implementation;

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
import com.underscoreresearch.backup.utils.LogUtil;
import com.underscoreresearch.backup.utils.ManualStatusLogger;
import com.underscoreresearch.backup.utils.StateLogger;
import com.underscoreresearch.backup.utils.StatusLine;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.utils.LogUtil.readableEta;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

@Slf4j
public class FileDownloaderImpl implements FileDownloader, ManualStatusLogger {
    private static final long GB = 1024 * 1024 * 1024;
    private final MetadataRepository repository;
    private final FileSystemAccess fileSystemAccess;
    private final AtomicBoolean shutdown = new AtomicBoolean();
    private final TreeMap<String, Progress> activeFiles = new TreeMap<>();

    public FileDownloaderImpl(MetadataRepository repository,
                              FileSystemAccess fileSystemAccess) {
        StateLogger.addLogger(this);

        this.repository = repository;
        this.fileSystemAccess = fileSystemAccess;
    }

    public static boolean isNullFile(String file) {
        return (file != null && (file.equals("-") || file.equals("=")));
    }

    @Override
    public void downloadFile(BackupFile source, String destinationFile, String password) throws IOException {
        Progress progress = new Progress(source.getLength());
        synchronized (activeFiles) {
            activeFiles.put(source.getPath(), progress);
        }

        try {
            for (int i = 0; true; i++) {
                try {
                    if (source.getLength() == 0) {
                        if (!isNullFile(destinationFile))
                            fileSystemAccess.completeFile(source, destinationFile, 0);
                    } else {
                        BackupLocation location = source.getLocations().get(i);
                        long offset = 0;

                        FileInputStream originalStream = null;
                        File originalFile = null;
                        if (destinationFile.equals("=")) {
                            originalFile = new File(PathNormalizer.physicalPath(source.getPath()));
                            if (!originalFile.isFile()) {
                                LogUtil.contentVerificationLogMessage(String.format("File missing locally \"%s\"", originalFile.getAbsolutePath()));
                                originalFile = null;
                            } else if (!originalFile.canRead()) {
                                LogUtil.contentVerificationLogMessage(String.format("Can't read file \"%s\" to compare", originalFile.getAbsolutePath()));
                                originalFile = null;
                            } else if (originalFile.length() != source.getLength()) {
                                LogUtil.contentVerificationLogMessage(String.format("File size does not match \"%s\" (\u200E%s\u200E in backup, \u200E%s\u200E locally})", originalFile.getAbsolutePath(),
                                        readableSize(source.getLength()), readableSize(originalFile.length())));
                                originalFile = null;
                            }

                            if (originalFile != null) {
                                try {
                                    originalStream = new FileInputStream(originalFile);
                                } catch (IOException exc) {
                                    log.warn("Can't open file \"{}\" to compare", originalFile.getAbsolutePath());
                                }
                            }
                        }

                        try {
                            for (BackupFilePart part : location.getParts()) {
                                List<BackupBlock> blocks = BackupBlock.expandBlock(part.getBlockHash(), repository);
                                for (BackupBlock block : blocks) {
                                    if (block == null) {
                                        throw new IOException(String.format("File referenced block \"%s\" that doesn't exist",
                                                part.getBlockHash()));
                                    }
                                    FileBlockExtractor extractor = BlockFormatFactory.getExtractor(block.getFormat());
                                    try {
                                        byte[] fileData = extractor.extractPart(part, block, password);
                                        if (fileData == null) {
                                            throw new IOException("Failed to extra data for part of block \"" + block.getHash() + "\"");
                                        }

                                        if (!isNullFile(destinationFile)) {
                                            fileSystemAccess.writeData(destinationFile, fileData, offset, fileData.length);
                                        } else if (destinationFile.equals("=")) {
                                            for (int originalOffset = 0; originalStream != null && originalOffset < fileData.length; originalOffset += 8192) {
                                                byte[] original = new byte[8192];
                                                int length = Math.min(8192, fileData.length - originalOffset);
                                                if (originalStream.read(original, 0, length) != length) {
                                                    throw new IOException("Unexpected end of stream");
                                                }
                                                for (int j = 0; j < length; j++)
                                                    if (original[j] != fileData[j + originalOffset]) {
                                                        String message = String.format("File \"%s\" does not match locally at location \"%s\"",
                                                                PathNormalizer.physicalPath(source.getPath()), offset + j);

                                                        if (Files.getLastModifiedTime(originalFile.toPath()).toMillis() == source.getLastChanged())
                                                            throw new IOException(message);

                                                        LogUtil.contentVerificationLogMessage(message);
                                                        originalStream.close();
                                                        originalStream = null;
                                                        originalFile = null;
                                                        break;
                                                    }
                                            }
                                        }

                                        offset += fileData.length;
                                        if ((offset - fileData.length) / GB != offset / GB) {
                                            log.info("Processed {} / {} for \"{}\"", readableSize(offset), readableSize(source.getLength()),
                                                    PathNormalizer.physicalPath(source.getPath()));
                                        }

                                        progress.setCompleted(offset);
                                    } catch (Exception exc) {
                                        throw new IOException("Failed to download \"" + PathNormalizer.physicalPath(source.getPath())
                                                + " \"because missing or corrupt block \"" + block.getHash() + "\"", exc);
                                    }
                                }
                            }
                        } finally {
                            if (originalStream != null)
                                originalStream.close();
                        }

                        if (!isNullFile(destinationFile))
                            fileSystemAccess.completeFile(source, destinationFile, offset);

                        if (offset != source.getLength()) {
                            throw new IOException(String.format("Expected file \"%s\" to be of size \u200E%s\u200E but was actually \u200E%s\u200E",
                                    PathNormalizer.physicalPath(source.getPath()), source.getLength(), offset));
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
            return activeFiles.entrySet().stream().map(entry -> {
                if (entry.getValue().getCompleted() == 0)
                    return null;

                Duration duration = Duration.ofMillis(Instant.now().toEpochMilli() - entry.getValue().getStarted().toEpochMilli());
                if (duration.toSeconds() > 5) {
                    return new StatusLine(getClass(),
                            "DOWNLOADED_ACTIVE_" + entry.getKey(),
                            "Downloading " + entry.getKey(),
                            entry.getValue().getCompleted(),
                            entry.getValue().getTotal(),
                            readableSize(entry.getValue().getCompleted()) + " / "
                                    + readableSize(entry.getValue().getTotal())
                                    + readableEta(entry.getValue().getCompleted(), entry.getValue().getTotal(), duration));
                } else {
                    return new StatusLine(getClass(),
                            "DOWNLOADED_ACTIVE_" + entry.getKey(),
                            "Downloading " + entry.getKey(),
                            entry.getValue().getCompleted(),
                            entry.getValue().getTotal(),
                            readableSize(entry.getValue().getCompleted()) + " / "
                                    + readableSize(entry.getValue().getTotal()));
                }
            }).filter(Objects::nonNull).collect(Collectors.toList());
        }
    }

    @Data
    private static class Progress {
        private long completed;
        private long total;
        private Instant started;

        public Progress(long total) {
            this.total = total;
            started = Instant.now();
        }
    }
}
