package com.underscoreresearch.backup.file.implementation;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableMap;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.file.MapSerializer;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.MetadataRepositoryStorage;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockAdditional;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupPartialFile;
import com.underscoreresearch.backup.model.BackupPendingSet;
import com.underscoreresearch.backup.model.BackupUpdatedFile;
import com.underscoreresearch.backup.model.ExternalBackupFile;

public class NullRepository implements MetadataRepository {
    private static CloseableLock getDummyLock() {
        return new CloseableLock() {
            @Override
            public void close() {
            }

            @Override
            public boolean requested() {
                return false;
            }
        };
    }

    @Override
    public void addFile(BackupFile file) throws IOException {
    }

    @Override
    public String lastSyncedLogFile(String share) {
        return null;
    }

    @Override
    public void setLastSyncedLogFile(String share, String entry) {

    }

    @Override
    public void addAdditionalBlock(BackupBlockAdditional block) throws IOException {

    }

    @Override
    public BackupBlockAdditional additionalBlock(String publicKey, String blockHash) throws IOException {
        return null;
    }

    @Override
    public CloseableLock acquireUpdateLock() {
        return getDummyLock();
    }

    @Override
    public void deleteAdditionalBlock(String publicKey, String blockHash) {

    }

    @Override
    public boolean addUpdatedFile(BackupUpdatedFile file, long howOftenMs) {
        return false;
    }

    @Override
    public void removeUpdatedFile(BackupUpdatedFile file) {

    }

    @Override
    public CloseableStream<BackupUpdatedFile> getUpdatedFiles() throws IOException {
        return null;
    }

    @Override
    public void upgradeStorage() {
    }

    @Override
    public MetadataRepositoryStorage createStorageRevision() throws IOException {
        return null;
    }

    @Override
    public void cancelStorageRevision(MetadataRepositoryStorage newStorage) throws IOException {

    }

    @Override
    public void installStorageRevision(MetadataRepositoryStorage newStorage) throws IOException {

    }

    @Override
    public boolean isErrorsDetected() {
        return false;
    }

    @Override
    public void setErrorsDetected(boolean errorsDetected) throws IOException {

    }

    @Override
    public <K, V> CloseableMap<K, V> temporaryMap(MapSerializer<K, V> serializer) throws IOException {
        return null;
    }

    @Override
    public CloseableLock exclusiveLock() throws IOException {
        return null;
    }

    @Override
    public List<ExternalBackupFile> file(String path) throws IOException {
        return null;
    }

    @Override
    public BackupFile file(String path, Long timestamp) throws IOException {
        return null;
    }

    @Override
    public boolean deleteFile(BackupFile file) throws IOException {
        return false;
    }

    @Override
    public List<BackupFilePart> existingFilePart(String partHash) throws IOException {
        return null;
    }

    @Override
    public boolean deleteFilePart(BackupFilePart filePart) throws IOException {
        return false;
    }

    @Override
    public void addBlock(BackupBlock block) throws IOException {
    }

    @Override
    public BackupBlock block(String hash) throws IOException {
        return null;
    }

    @Override
    public boolean deleteBlock(BackupBlock block) throws IOException {
        return false;
    }

    @Override
    public void addTemporaryBlock(BackupBlock block) {
    }

    @Override
    public void installTemporaryBlocks() {
    }

    @Override
    public void addDirectory(BackupDirectory directory) throws IOException {
    }

    @Override
    public BackupDirectory directory(String path, Long timestamp, boolean accumulative) throws IOException {
        return null;
    }

    @Override
    public boolean deleteDirectory(String path, long timestamp) throws IOException {
        return false;
    }

    @Override
    public void pushActivePath(String setId, String path, BackupActivePath pendingFiles) throws IOException {
    }

    @Override
    public boolean hasActivePath(String setId, String path) {
        return false;
    }

    @Override
    public void popActivePath(String setId, String path) throws IOException {
    }

    @Override
    public boolean deletePartialFile(BackupPartialFile file) throws IOException {
        return false;
    }

    @Override
    public void savePartialFile(BackupPartialFile file) throws IOException {

    }

    @Override
    public void clearPartialFiles() {

    }

    @Override
    public BackupPartialFile getPartialFile(BackupPartialFile file) throws IOException {
        return null;
    }

    @Override
    public TreeMap<String, BackupActivePath> getActivePaths(String setId) throws IOException {
        return null;
    }

    @Override
    public void flushLogging() throws IOException {
    }

    @Override
    public void open(boolean readOnly) throws IOException {
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public CloseableStream<BackupFile> allFiles(boolean ascending) throws IOException {
        return null;
    }

    @Override
    public CloseableStream<BackupBlock> allBlocks() throws IOException {
        return null;
    }

    @Override
    public CloseableStream<BackupBlockAdditional> allAdditionalBlocks() {
        return null;
    }

    @Override
    public CloseableStream<BackupFilePart> allFileParts() throws IOException {
        return null;
    }

    @Override
    public CloseableStream<BackupDirectory> allDirectories(boolean ascending) throws IOException {
        return null;
    }

    @Override
    public void addPendingSets(BackupPendingSet scheduledTime) throws IOException {

    }

    @Override
    public void deletePendingSets(String setId) throws IOException {

    }

    @Override
    public Set<BackupPendingSet> getPendingSets() throws IOException {
        return null;
    }

    @Override
    public CloseableLock acquireLock() {
        return getDummyLock();
    }

    @Override
    public long getBlockCount() {
        return 0;
    }

    @Override
    public long getFileCount() {
        return 0;
    }

    @Override
    public long getDirectoryCount() {
        return 0;
    }

    @Override
    public long getPartCount() {
        return 0;
    }

    @Override
    public void clear() throws IOException {

    }

}
