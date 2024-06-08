package com.underscoreresearch.backup.file.implementation;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.file.PathNormalizer.normalizePath;
import static com.underscoreresearch.backup.io.IOUtils.createDirectory;
import static com.underscoreresearch.backup.io.IOUtils.deleteFileException;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.SystemUtils;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.utils.ManualStatusLogger;
import com.underscoreresearch.backup.utils.StateLogger;
import com.underscoreresearch.backup.utils.StatusLine;

@Slf4j
public class FileSystemAccessImpl implements FileSystemAccess, ManualStatusLogger {
    private static final List<StatusLine> EMPTY_LIST = Lists.newArrayList();
    private final AtomicLong processedFiles = new AtomicLong();
    private File activePath;
    private int activeFiles;
    private boolean registered;

    @Override
    public Set<BackupFile> directoryFiles(String path) {
        if (path.endsWith(PATH_SEPARATOR)) {
            path = path.substring(0, path.length() - 1);
        }

        TreeSet<BackupFile> files = new TreeSet<>();

        if (SystemUtils.IS_OS_WINDOWS && path.isEmpty()) {
            for (File file : File.listRoots()) {
                files.add(BackupFile.builder()
                        .path(normalizePath(file.getPath()))
                        .build());
            }
        } else {
            File parent = new File(PathNormalizer.physicalPath(!path.contains(PATH_SEPARATOR) ? path + PATH_SEPARATOR : path));

            if (parent.isDirectory()) {
                String[] fileNames = parent.list();

                if (fileNames != null) {
                    Arrays.sort(fileNames);
                    try {
                        synchronized (processedFiles) {
                            activeFiles = fileNames.length;
                            processedFiles.set(0);
                            activePath = parent;
                            if (!registered) {
                                registered = true;
                                StateLogger.addLogger(this);
                            }
                        }
                        for (String fileName : fileNames) {
                            File file = new File(parent, fileName);
                            try {
                                Path filePath = file.toPath();
                                if (!isSymbolicLink(filePath)) {
                                    if (file.isDirectory()) {
                                        if (Files.isReadable(filePath)) {
                                            files.add(BackupFile.builder()
                                                    .path(path + PATH_SEPARATOR + fileName + PATH_SEPARATOR)
                                                    .build());
                                        } else {
                                            debug(() -> log.debug("Skipping unreadable directory \"" + filePath.toAbsolutePath() + "\""));
                                        }
                                    } else if (file.isFile()) {
                                        if (Files.isReadable(filePath)) {
                                            files.add(createBackupFile(path + PATH_SEPARATOR + fileName, file));
                                        } else {
                                            debug(() -> log.debug("Skipping unreadable file \"" + filePath.toAbsolutePath() + "\""));
                                        }
                                    }
                                }
                            } catch (InvalidPathException exc) {
                                log.warn("Skipping invalid path \"{}\"", file.getAbsolutePath(), exc);
                            } catch (IOException e) {
                                log.warn("Failed to get last modified time for \"{}\"", parent.getAbsolutePath(), e);
                            }
                            processedFiles.incrementAndGet();
                        }
                    } finally {
                        synchronized (processedFiles) {
                            activePath = null;
                            activeFiles = 0;
                        }
                    }
                } else {
                    log.warn("Failed to get list of files for \"" + parent + "\"");
                }
            } else if (parent.exists()) {
                try {
                    Path parentPath = parent.toPath();
                    if (!Files.isSymbolicLink(parentPath) && parent.isFile()) {
                        if (Files.isReadable(parentPath)) {
                            files.add(createBackupFile(path, parent));
                        } else {
                            debug(() -> log.debug("Skipping unreadable file \"" + parentPath.toAbsolutePath() + "\""));
                        }
                    }
                } catch (InvalidPathException exc) {
                    log.warn("Skipping invalid path \"{}\"", parent.getAbsolutePath(), exc);
                } catch (IOException e) {
                    log.warn("Failed to get last modified time for \"{}\"", parent.getAbsolutePath(), e);
                }
            }
        }
        return files;
    }

    protected boolean isSymbolicLink(Path filePath) {
        return Files.isSymbolicLink(filePath);
    }

    @Override
    public String extractPermissions(String path) {
        return null;
    }

    @Override
    public void applyPermissions(File path, String permissions) throws IOException {

    }

    protected BackupFile createBackupFile(String path, File file) throws IOException {
        return BackupFile.builder()
                .path(path)
                .length(file.length())
                .lastChanged(Files.getLastModifiedTime(file.toPath()).toMillis())
                .build();
    }

    @Override
    public int readData(String path, byte[] buffer, long offset, int length) throws IOException {
        File file = new File(PathNormalizer.physicalPath(path));
        try (RandomAccessFile stream = new RandomAccessFile(file, "r")) {
            try (FileChannel ch = stream.getChannel()) {
                return ch.read(ByteBuffer.wrap(buffer, 0, length), offset);
            }
        }
    }

    @Override
    public void writeData(String path, byte[] buffer, long offset, int length) throws IOException {
        File file = new File(PathNormalizer.physicalPath(path));
        createDirectory(file.getParentFile(), true);
        try (RandomAccessFile stream = new RandomAccessFile(file, "rw")) {
            try (FileChannel ch = stream.getChannel()) {
                if (ch.write(ByteBuffer.wrap(buffer, 0, length), offset) != length) {
                    throw new IOException("Failed to write data to file \"" + file.getAbsolutePath() + "\"");
                }
            }
        }
    }

    @Override
    public void completeFile(BackupFile backupFile, String path, long length) throws IOException {
        File file = new File(PathNormalizer.physicalPath(path));
        if (file.length() != length) {
            try (RandomAccessFile stream = new RandomAccessFile(file, "rw")) {
                try (FileChannel ch = stream.getChannel()) {
                    ch.truncate(length);
                }
            }
        }

        applyPermissions(file, backupFile.getPermissions());
    }

    @Override
    public void delete(String path) throws IOException {
        File file = new File(PathNormalizer.physicalPath(path));
        deleteFileException(file);
    }

    @Override
    public void resetStatus() {
        synchronized (processedFiles) {
            processedFiles.set(0);
            activePath = null;
            activeFiles = 0;
        }
    }

    @Override
    public List<StatusLine> status() {
        synchronized (processedFiles) {
            if (processedFiles.get() > 0) {
                return Lists.newArrayList(new StatusLine(getClass(), "READING_DIRECTORY",
                        String.format("Reading directory \"%s\"", activePath), processedFiles.get(),
                        readableSize(processedFiles.get()) + " / "
                                + readableSize(activeFiles)));
            }
            return EMPTY_LIST;
        }
    }
}
