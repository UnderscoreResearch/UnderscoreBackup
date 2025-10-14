package com.underscoreresearch.backup.block.assignments;

import com.underscoreresearch.backup.block.BlockDownloader;
import com.underscoreresearch.backup.block.BlockFormatPlugin;
import com.underscoreresearch.backup.block.FileBlockUploader;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.machinestate.MachineState;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Block assignment implementation for large files that uses GZIP compression.
 * Extends LargeFileBlockAssignment to add compression functionality.
 */
@Slf4j
@BlockFormatPlugin("GZIP")
public class GzipLargeFileBlockAssignment extends LargeFileBlockAssignment {
    
    /**
     * Constructor for GzipLargeFileBlockAssignment.
     * 
     * @param uploader Block uploader for storing blocks
     * @param blockDownloader Block downloader for retrieving blocks
     * @param access File system access for reading files
     * @param metadataRepository Repository for metadata
     * @param machineState State machine for tracking progress
     * @param encryptionIdentity Encryption identity for securing data
     * @param maximumBlockSize Maximum size of blocks
     */
    public GzipLargeFileBlockAssignment(FileBlockUploader uploader, BlockDownloader blockDownloader,
                                        FileSystemAccess access, MetadataRepository metadataRepository,
                                        MachineState machineState, EncryptionIdentity encryptionIdentity, int maximumBlockSize) {
        super(uploader, blockDownloader, access, metadataRepository, machineState, encryptionIdentity, maximumBlockSize);
    }

    /**
     * Process a buffer by compressing it with GZIP.
     * 
     * @param buffer The buffer to compress
     * @return The compressed buffer
     * @throws IOException If there's an error during compression
     */
    @Override
    protected byte[] processBuffer(byte[] buffer) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
                gzipOutputStream.write(buffer, 0, buffer.length);
            }
            return outputStream.toByteArray();
        }
    }

    /**
     * Extract a file part from block data by decompressing it.
     * 
     * @param file The file part to extract
     * @param blockData The compressed block data
     * @return The decompressed data
     * @throws IOException If there's an error during decompression
     */
    @Override
    public byte[] extractPart(BackupFilePart file, byte[] blockData) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(blockData)) {
            try (GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
                return IOUtils.readAllBytes(gzipInputStream);
            }
        }
    }

    /**
     * Get the format identifier for this block assignment.
     * 
     * @return The format identifier string
     */
    @Override
    protected String getFormat() {
        return "GZIP";
    }
}
