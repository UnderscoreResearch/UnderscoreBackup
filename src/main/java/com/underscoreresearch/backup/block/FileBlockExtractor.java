package com.underscoreresearch.backup.block;

import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupFilePart;

import java.io.IOException;

/**
 * Interface for extracting file parts from backup blocks.
 * Handles the process of retrieving file data from stored blocks.
 */
public interface FileBlockExtractor {
    /**
     * Extract a file part from a backup block.
     * 
     * @param file The file part to extract
     * @param block The block containing the file part
     * @param password The password to decrypt the block
     * @return The extracted file part data as a byte array
     * @throws IOException If there's an error extracting the file part
     */
    byte[] extractPart(BackupFilePart file, BackupBlock block, String password) throws IOException;

    /**
     * Calculate the size of a block for a specific file part.
     * 
     * @param file The file part
     * @param blockData The block data
     * @return The size of the block in bytes
     * @throws IOException If there's an error calculating the block size
     */
    long blockSize(BackupFilePart file, byte[] blockData) throws IOException;
}
