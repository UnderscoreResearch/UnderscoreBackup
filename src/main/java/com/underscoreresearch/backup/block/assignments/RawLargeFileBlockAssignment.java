package com.underscoreresearch.backup.block.assignments;

import com.underscoreresearch.backup.block.BlockDownloader;
import com.underscoreresearch.backup.block.BlockFormatPlugin;
import com.underscoreresearch.backup.block.FileBlockUploader;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.machinestate.MachineState;

import java.io.IOException;

/**
 * Block assignment implementation for large files that stores data without compression.
 * Extends LargeFileBlockAssignment to provide raw data storage.
 */
@BlockFormatPlugin("RAW")
public class RawLargeFileBlockAssignment extends LargeFileBlockAssignment {
    
    /**
     * Constructor for RawLargeFileBlockAssignment.
     * 
     * @param uploader Block uploader for storing blocks
     * @param downloader Block downloader for retrieving blocks
     * @param access File system access for reading files
     * @param metadataRepository Repository for metadata
     * @param machineState State machine for tracking progress
     * @param encryptionIdentity Encryption identity for securing data
     * @param maximumBlockSize Maximum size of blocks
     */
    public RawLargeFileBlockAssignment(FileBlockUploader uploader, BlockDownloader downloader, FileSystemAccess access,
                                       MetadataRepository metadataRepository, MachineState machineState,
                                       EncryptionIdentity encryptionIdentity, int maximumBlockSize) {
        super(uploader, downloader, access, metadataRepository, machineState, encryptionIdentity, maximumBlockSize);
    }

    /**
     * Process a buffer by returning it unchanged (no compression).
     * 
     * @param buffer The buffer to process
     * @return The same buffer without modification
     * @throws IOException If there's an error during processing
     */
    protected byte[] processBuffer(byte[] buffer) throws IOException {
        return buffer;
    }

    /**
     * Get the format identifier for this block assignment.
     * 
     * @return The format identifier string
     */
    protected String getFormat() {
        return "RAW";
    }

    /**
     * Extract a file part from block data by returning it unchanged.
     * 
     * @param file The file part to extract
     * @param blockData The block data
     * @return The same block data without modification
     * @throws IOException If there's an error during extraction
     */
    @Override
    public byte[] extractPart(BackupFilePart file, byte[] blockData) throws IOException {
        return blockData;
    }
}
