package com.underscoreresearch.backup.file.implementation;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.file.PathNormalizer.normalizePath;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.SystemUtils;

import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.model.BackupFile;

@Slf4j
public class FileSystemAccessImpl implements FileSystemAccess {
    @Override
    public Set<BackupFile> directoryFiles(String path) {
        if (path.endsWith(PATH_SEPARATOR)) {
            path = path.substring(0, path.length() - 1);
        }

        TreeSet<BackupFile> files = new TreeSet<>();

        if (SystemUtils.IS_OS_WINDOWS && path.length() == 0) {
            for (File file : File.listRoots()) {
                files.add(BackupFile.builder()
                        .path(normalizePath(file.getPath()))
                        .build());
            }
        } else {
            File parent = new File(PathNormalizer.physicalPath(path + PATH_SEPARATOR));

            if (parent.isDirectory()) {
                String[] fileNames = parent.list();
                Arrays.sort(fileNames);

                if (fileNames != null) {
                    for (String fileName : fileNames) {
                        File file = new File(parent, fileName);
                        Path filePath = file.toPath();
                        if (!Files.isSymbolicLink(filePath)) {
                            if (file.isDirectory()) {
                                if (Files.isReadable(filePath)) {
                                    files.add(BackupFile.builder()
                                            .path(path + PATH_SEPARATOR + fileName + PATH_SEPARATOR)
                                            .build());
                                } else {
                                    log.debug("Skipping unreadable directory " + filePath.toAbsolutePath());
                                }
                            } else if (file.isFile()) {
                                if (Files.isReadable(filePath)) {
                                    files.add(BackupFile.builder()
                                            .path(path + PATH_SEPARATOR + fileName)
                                            .length(file.length())
                                            .lastChanged(file.lastModified())
                                            .build());
                                } else {
                                    log.debug("Skipping unreadable file " + filePath.toAbsolutePath());
                                }
                            }
                        }
                    }
                } else {
                    log.warn("Failed to get list of files for " + parent);
                }
            }
        }
        return files;
    }

    @Override
    public int readData(String path, byte[] buffer, long offset, int length) throws IOException {
        File parent = new File(PathNormalizer.physicalPath(path));
        try (RandomAccessFile stream = new RandomAccessFile(parent, "r")) {
            try (FileChannel ch = stream.getChannel()) {
                return ch.read(ByteBuffer.wrap(buffer, 0, length), offset);
            }
        }
    }

    @Override
    public void writeData(String path, byte[] buffer, long offset, int length) throws IOException {
        File file = new File(PathNormalizer.physicalPath(path));
        ensureDirectoryExists(file);
        try (RandomAccessFile stream = new RandomAccessFile(file, "rw")) {
            try (FileChannel ch = stream.getChannel()) {
                ch.write(ByteBuffer.wrap(buffer, 0, length), offset);
            }
        }
    }

    @Override
    public void truncate(String path, long length) throws IOException {
        File file = new File(PathNormalizer.physicalPath(path));
        if (file.length() != length) {
            try (RandomAccessFile stream = new RandomAccessFile(file, "rw")) {
                try (FileChannel ch = stream.getChannel()) {
                    ch.truncate(length);
                }
            }
        }
    }

    @Override
    public void delete(String path) throws IOException {
        File file = new File(PathNormalizer.physicalPath(path));
        if (!file.delete()) {
            throw new IOException("Failed to delete file " + file.getAbsolutePath());
        }
    }

    private void ensureDirectoryExists(File file) {
        File parent = file.getParentFile();
        if (!parent.isDirectory()) {
            parent.mkdirs();
        }
    }
}
