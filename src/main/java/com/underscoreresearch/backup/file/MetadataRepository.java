package com.underscoreresearch.backup.file;

import java.io.IOException;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.underscoreresearch.backup.manifest.model.BackupDirectory;
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

    boolean deleteFilePart(BackupFilePart filePart) throws IOException;

    void addBlock(BackupBlock block) throws IOException;

    BackupBlock block(String hash) throws IOException;

    boolean deleteBlock(BackupBlock block) throws IOException;

    void addDirectory(BackupDirectory directory) throws IOException;

    List<BackupDirectory> directory(String path) throws IOException;

    BackupDirectory lastDirectory(String path) throws IOException;

    boolean deleteDirectory(String path, long timestamp) throws IOException;

    void pushActivePath(String setId, String path, BackupActivePath pendingFiles) throws IOException;

    boolean hasActivePath(String setId, String path) throws IOException;

    void popActivePath(String setId, String path) throws IOException;

    TreeMap<String, BackupActivePath> getActivePaths(String setId) throws IOException;

    void flushLogging() throws IOException;

    void open(boolean readOnly) throws IOException;

    void close() throws IOException;

    /**
     * All files in repository. Must be sorted by path and timestamp in descending order.
     */
    Stream<BackupFile> allFiles() throws IOException;

    Stream<BackupBlock> allBlocks() throws IOException;

    /**
     * All directories in repository. Must be sorted by path and timestamp in descending order.
     */
    Stream<BackupDirectory> allDirectories() throws IOException;
}
