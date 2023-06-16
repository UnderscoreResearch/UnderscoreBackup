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

    void addTemporaryBlock(BackupBlock block) throws IOException;

    void installTemporaryBlocks() throws IOException;

    void addDirectory(BackupDirectory directory) throws IOException;

    List<BackupDirectory> directory(String path) throws IOException;

    BackupDirectory lastDirectory(String path) throws IOException;

    boolean deleteDirectory(String path, long timestamp) throws IOException;

    void pushActivePath(String setId, String path, BackupActivePath pendingFiles) throws IOException;

    boolean hasActivePath(String setId, String path) throws IOException;

    void popActivePath(String setId, String path) throws IOException;

    boolean deletePartialFile(BackupPartialFile file) throws IOException;

    void savePartialFile(BackupPartialFile file) throws IOException;

    void clearPartialFiles() throws IOException;

    BackupPartialFile getPartialFile(BackupPartialFile file) throws IOException;

    TreeMap<String, BackupActivePath> getActivePaths(String setId) throws IOException;

    void flushLogging() throws IOException;

    void open(boolean readOnly) throws IOException;

    void close() throws IOException;

    CloseableStream<BackupFile> allFiles(boolean ascending) throws IOException;

    CloseableStream<BackupBlock> allBlocks() throws IOException;

    CloseableStream<BackupFilePart> allFileParts() throws IOException;

    CloseableStream<BackupDirectory> allDirectories(boolean ascending) throws IOException;

    void addPendingSets(BackupPendingSet scheduledTime) throws IOException;

    void deletePendingSets(String setId) throws IOException;

    Set<BackupPendingSet> getPendingSets() throws IOException;

    CloseableLock acquireLock();

    long getBlockCount() throws IOException;

    long getFileCount() throws IOException;

    long getDirectoryCount() throws IOException;

    long getPartCount() throws IOException;

    void clear() throws IOException;

    String lastSyncedLogFile(String share) throws IOException;

    void setLastSyncedLogFile(String share, String entry) throws IOException;

    void addAdditionalBlock(BackupBlockAdditional block) throws IOException;

    BackupBlockAdditional additionalBlock(String publicKey, String blockHash) throws IOException;

    CloseableLock acquireUpdateLock();

    void deleteAdditionalBlock(String publicKey, String blockHash) throws IOException;

    boolean addUpdatedFile(BackupUpdatedFile file, long howOftenMs) throws IOException;

    void removeUpdatedFile(BackupUpdatedFile file) throws IOException;

    CloseableStream<BackupUpdatedFile> getUpdatedFiles() throws IOException;

    void upgradeStorage() throws IOException;

    <K, V> CloseableMap<K, V> temporaryMap(MapSerializer<K, V> serializer) throws IOException;

    // Grant exclusive lock to the repository. All changes must happen on the granting thread.
    CloseableLock exclusiveLock() throws IOException;
}
