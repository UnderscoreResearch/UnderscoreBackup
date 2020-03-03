package com.underscoreresearch.backup.file;

import java.io.IOException;
import java.util.List;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;

public interface MetadataRepository {
    void addFile(BackupFile file) throws IOException;

    List<BackupFile> file(String path) throws IOException;

    BackupFile lastFile(String path) throws IOException;

    boolean deleteFile(BackupFile file) throws IOException;

    List<BackupFilePart> existingFilePart(String partHash) throws IOException;

    Stream<BackupFile> allFiles() throws IOException;

    Stream<BackupBlock> allBlocks() throws IOException;

    boolean deleteFilePart(BackupFilePart filePart) throws IOException;

    void addBlock(BackupBlock block) throws IOException;

    BackupBlock block(String hash) throws IOException;

    boolean deleteBlock(BackupBlock block) throws IOException;

    void addDirectory(String path, Long timestamp, Set<String> files) throws IOException;

    NavigableMap<Long, NavigableSet<String>> directory(String path) throws IOException;

    NavigableSet<String> lastDirectory(String path) throws IOException;

    boolean deleteDirectory(String path, Long timestamp) throws IOException;

    void pushActivePath(String setId, String path, BackupActivePath pendingFiles) throws IOException;

    boolean hasActivePath(String setId, String path) throws IOException;

    void popActivePath(String setId, String path) throws IOException;

    TreeMap<String, BackupActivePath> getActivePaths(String setId) throws IOException;

    void flushLogging() throws IOException;

    void open(boolean readOnly) throws IOException;

    void close() throws IOException;
}
