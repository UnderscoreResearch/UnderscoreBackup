package com.underscoreresearch.backup.file;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockAdditional;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupPartialFile;
import com.underscoreresearch.backup.model.BackupPendingSet;
import com.underscoreresearch.backup.model.BackupUpdatedFile;

public interface MetadataRepositoryStorage {
    void open(boolean readOnly) throws IOException;

    void close();

    List<BackupFile> file(String path) throws IOException;

    CloseableStream<BackupFile> allFiles(boolean ascending) throws IOException;

    CloseableStream<BackupBlock> allBlocks() throws IOException;

    List<BackupFilePart> existingFilePart(String partHash) throws IOException;

    CloseableStream<BackupFilePart> allFileParts() throws IOException;

    CloseableStream<BackupDirectory> allDirectories(boolean ascending) throws IOException;

    CloseableStream<BackupBlockAdditional> allAdditionalBlocks() throws IOException;

    void addPendingSets(BackupPendingSet scheduledTime) throws IOException;

    void deletePendingSets(String setId) throws IOException;

    Set<BackupPendingSet> getPendingSets() throws IOException;

    BackupFile lastFile(String path) throws IOException;

    BackupBlock block(String hash) throws IOException;

    List<BackupDirectory> directory(String path) throws IOException;

    BackupDirectory lastDirectory(String path) throws IOException;

    void addFile(BackupFile file) throws IOException;

    void addFilePart(BackupFilePart part) throws IOException;

    void addBlock(BackupBlock block) throws IOException;

    void addTemporaryBlock(BackupBlock block) throws IOException;

    void switchBlocksTable() throws IOException;

    void addDirectory(BackupDirectory directory) throws IOException;

    boolean deleteBlock(BackupBlock block) throws IOException;

    boolean deleteFile(BackupFile file) throws IOException;

    boolean deleteFilePart(BackupFilePart part) throws IOException;

    boolean deleteDirectory(String path, long timestamp) throws IOException;

    void pushActivePath(String setId, String path, BackupActivePath pendingFiles) throws IOException;

    boolean hasActivePath(String setId, String path) throws IOException;

    void popActivePath(String setId, String path) throws IOException;

    boolean deletePartialFile(BackupPartialFile file) throws IOException;

    void savePartialFile(BackupPartialFile file) throws IOException;

    void clearPartialFiles() throws IOException;

    BackupPartialFile getPartialFile(BackupPartialFile file) throws IOException;

    TreeMap<String, BackupActivePath> getActivePaths(String setId) throws IOException;

    long getBlockCount() throws IOException;

    long getFileCount() throws IOException;

    long getDirectoryCount() throws IOException;

    long getPartCount() throws IOException;

    long getAdditionalBlockCount() throws IOException;

    long getUpdatedFileCount() throws IOException;

    void addAdditionalBlock(BackupBlockAdditional block) throws IOException;

    BackupBlockAdditional additionalBlock(String publicKey, String blockHash) throws IOException;

    void deleteAdditionalBlock(String publicKey, String blockHash) throws IOException;

    boolean addUpdatedFile(BackupUpdatedFile file, long howOften) throws IOException;

    void removeUpdatedFile(BackupUpdatedFile file) throws IOException;

    CloseableStream<BackupUpdatedFile> getUpdatedFiles() throws IOException;

    void clear() throws IOException;

    void commit() throws IOException;

    boolean needPeriodicCommits();

    <K, V> CloseableMap<K, V> temporaryMap(MapSerializer<K, V> serializer) throws IOException;

    boolean needExclusiveCommitLock();
}