package com.underscoreresearch.backup.block;

import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;

import java.io.IOException;
import java.util.Set;

/**
 * Interface for downloading backup blocks from storage destinations.
 */
public interface BlockDownloader {
    /**
     * Download a backup block and decrypt it using the provided password.
     * 
     * @param block The backup block to download
     * @param password The password to decrypt the block
     * @return The decrypted block data as a byte array
     * @throws IOException If there's an error downloading or decrypting the block
     */
    byte[] downloadBlock(BackupBlock block, String password) throws IOException;

    /**
     * Download an encrypted block from a specific storage destination.
     * 
     * @param block The backup block to download
     * @param storage The specific storage location to download from
     * @param available Set of available storage destinations
     * @return The encrypted block data as a byte array
     * @throws IOException If there's an error downloading the block
     */
    byte[] downloadEncryptedBlockStorage(BackupBlock block, BackupBlockStorage storage, Set<String> available) throws IOException;

    /**
     * Shutdown the downloader and release any resources.
     */
    void shutdown();
}
