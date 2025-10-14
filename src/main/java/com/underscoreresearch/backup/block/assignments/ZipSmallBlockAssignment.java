package com.underscoreresearch.backup.block.assignments;

import com.underscoreresearch.backup.block.BlockDownloader;
import com.underscoreresearch.backup.block.BlockFormatPlugin;
import com.underscoreresearch.backup.block.FileBlockExtractor;
import com.underscoreresearch.backup.block.FileBlockUploader;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupBlock;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static com.underscoreresearch.backup.block.assignments.ZipSmallBlockAssignment.FORMAT;

/**
 * Block assignment implementation that handles small files with ZIP compression.
 * Files are stored in a ZIP archive with optional additional GZIP compression for larger files.
 */
@Slf4j
@BlockFormatPlugin(FORMAT)
public class ZipSmallBlockAssignment extends SmallFileBlockAssignment implements FileBlockExtractor {
    public static final String FORMAT = "ZIP";

    /**
     * Constructor for ZipSmallBlockAssignment.
     * 
     * @param uploader Block uploader for storing blocks
     * @param blockDownloader Block downloader for retrieving blocks
     * @param repository Metadata repository for block information
     * @param access File system access for reading files
     * @param encryptionIdentity Encryption identity for securing data
     * @param maximumFileSize Maximum size of files to handle
     * @param targetSize Target size for blocks
     */
    public ZipSmallBlockAssignment(FileBlockUploader uploader,
                                   BlockDownloader blockDownloader,
                                   MetadataRepository repository,
                                   FileSystemAccess access,
                                   EncryptionIdentity encryptionIdentity,
                                   int maximumFileSize,
                                   int targetSize) {
        super(uploader, blockDownloader, repository, access, encryptionIdentity, maximumFileSize, targetSize);
    }

    /**
     * Create a new pending file for ZIP data.
     * 
     * @return A new ZipPendingFile instance
     */
    @Override
    protected PendingFile createPendingFile() {
        return new ZipPendingFile();
    }

    /**
     * Get the format identifier for this block assignment.
     * 
     * @return The format identifier string
     */
    @Override
    protected String getFormat() {
        return FORMAT;
    }

    /**
     * Create a cached data object for ZIP blocks.
     * 
     * @param key The block hash key
     * @param password The password for decryption
     * @return A new ZipCachedData instance
     */
    @Override
    protected CachedData createCacheData(String key, String password) {
        return new ZipCachedData(key, password);
    }

    /**
     * Inner class for storing cached entries with optional compression.
     */
    @Data
    @AllArgsConstructor
    private static class CacheEntry {
        private boolean compressed;
        private byte[] data;

        /**
         * Get the data, decompressing if necessary.
         * 
         * @return The decompressed data
         * @throws IOException If there's an error decompressing
         */
        public byte[] get() throws IOException {
            if (compressed) {
                try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
                    try (GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
                        return IOUtils.readAllBytes(gzipInputStream);
                    }
                }
            }
            return data;
        }
    }

    /**
     * Inner class for handling cached ZIP block data.
     */
    private class ZipCachedData extends CachedData {
        private static final long MINIMUM_COMPRESSED_SIZE = 8192;
        private static final long MINIMUM_COMPRESSED_RATIO = 2;
        private final String hash;
        private final Map<String, CacheEntry> blockEntries;

        /**
         * Constructor that loads and extracts ZIP block data.
         * 
         * @param hash The block hash
         * @param password The password for decryption
         */
        private ZipCachedData(String hash, String password) {
            this.hash = hash;
            try {
                blockEntries = new HashMap<>();

                BackupBlock block = getRepository().block(hash);
                if (block == null) {
                    throw new IOException(String.format("Trying to get unknown block \"%s\"", hash));
                }

                try (ByteArrayInputStream inputStream = new ByteArrayInputStream(getBlockDownloader()
                        .downloadBlock(block, password))) {
                    try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
                        ZipEntry ze;
                        while ((ze = zipInputStream.getNextEntry()) != null) {
                            byte[] data = IOUtils.readAllBytes(zipInputStream);
                            if (ze.getSize() > MINIMUM_COMPRESSED_SIZE
                                    && ze.getSize() / ze.getCompressedSize() > MINIMUM_COMPRESSED_RATIO) {
                                try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                                    try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
                                        gzipOutputStream.write(data);
                                    }
                                    blockEntries.put(ze.getName(), new CacheEntry(true, byteArrayOutputStream.toByteArray()));
                                }
                            } else {
                                blockEntries.put(ze.getName(), new CacheEntry(false, data));
                            }
                        }
                    }
                }
            } catch (IOException exc) {
                throw new RuntimeException(exc);
            }
        }

        /**
         * Get a specific part from the cached block data.
         * 
         * @param index The index of the part
         * @param partHash The hash of the part (unused in this implementation)
         * @return The part data
         * @throws IOException If there's an error getting the part
         */
        public byte[] get(int index, String partHash) throws IOException {
            CacheEntry entry = blockEntries.get(String.valueOf(index));
            if (entry != null) {
                return entry.get();
            }
            return null;
        }

        /**
         * Check if this cached data equals another object.
         * 
         * @param o The object to compare with
         * @return true if the objects are equal, false otherwise
         */
        @Override
        public boolean equals(Object o) {
            if (o instanceof ZipCachedData that) {
                return Objects.equals(hash, that.hash);
            }
            return false;
        }

        /**
         * Generate a hash code for this cached data.
         * 
         * @return The hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), hash);
        }
    }

    /**
     * Inner class for handling pending ZIP file data.
     */
    private class ZipPendingFile extends PendingFile {
        private ByteArrayOutputStream output = new ByteArrayOutputStream(getTargetSize());
        private ZipOutputStream zipOutputStream = new ZipOutputStream(output);

        /**
         * Add a part to the pending ZIP file.
         * 
         * @param index The index of the part
         * @param data The data to add
         * @param partHash The hash of the part (unused in this implementation)
         * @throws IOException If there's an error adding the part
         */
        @Override
        protected void addPartData(int index, byte[] data, String partHash) throws IOException {
            ZipEntry entry = new ZipEntry(String.valueOf(index));
            zipOutputStream.putNextEntry(entry);
            zipOutputStream.write(data, 0, data.length);
            zipOutputStream.closeEntry();
            zipOutputStream.flush();
        }

        /**
         * Estimate the current size of the pending ZIP file.
         * 
         * @return The estimated size in bytes
         */
        @Override
        public synchronized int estimateSize() {
            return output.size();
        }

        /**
         * Get the complete data for the pending ZIP file.
         * 
         * @return The complete ZIP data as a byte array
         * @throws IOException If there's an error finalizing the ZIP
         */
        @Override
        public synchronized byte[] data() throws IOException {
            zipOutputStream.close();
            zipOutputStream = null;
            byte[] data = output.toByteArray();
            output.close();
            output = null;
            return data;
        }
    }
}
