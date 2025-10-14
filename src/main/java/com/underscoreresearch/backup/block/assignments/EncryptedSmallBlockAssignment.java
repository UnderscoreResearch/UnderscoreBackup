package com.underscoreresearch.backup.block.assignments;

import com.underscoreresearch.backup.block.BlockDownloader;
import com.underscoreresearch.backup.block.BlockFormatPlugin;
import com.underscoreresearch.backup.block.FileBlockExtractor;
import com.underscoreresearch.backup.block.FileBlockUploader;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupBlock;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.underscoreresearch.backup.block.assignments.EncryptedSmallBlockAssignment.FORMAT;

/**
 * Block assignment implementation that handles small files with encryption.
 * Files are compressed with GZIP and encrypted with AES before storage.
 */
@Slf4j
@BlockFormatPlugin(FORMAT)
public class EncryptedSmallBlockAssignment extends SmallFileBlockAssignment implements FileBlockExtractor {
    /**
     * Format identifier for encrypted small block assignments.
     */
    public static final String FORMAT = "ENC";
    
    /**
     * The encryption algorithm used for securing block data.
     */
    private static final String ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding";
    
    /**
     * The key algorithm used for encryption.
     */
    private static final String KEY_ALGORITHM = "AES";
    
    /**
     * Size of the initialization vector used in encryption.
     */
    private static final int IV_SIZE = 16;

    /**
     * Constructor for EncryptedSmallBlockAssignment.
     * 
     * @param uploader Block uploader for storing blocks
     * @param blockDownloader Block downloader for retrieving blocks
     * @param repository Metadata repository for block information
     * @param access File system access for reading files
     * @param encryptionIdentity Encryption identity for securing data
     * @param maximumFileSize Maximum size of files to handle
     * @param targetSize Target size for blocks
     */
    public EncryptedSmallBlockAssignment(FileBlockUploader uploader,
                                         BlockDownloader blockDownloader,
                                         MetadataRepository repository,
                                         FileSystemAccess access,
                                         EncryptionIdentity encryptionIdentity,
                                         int maximumFileSize,
                                         int targetSize) {
        super(uploader, blockDownloader, repository, access, encryptionIdentity, maximumFileSize, targetSize);
    }

    /**
     * Create a new pending file for encrypted data.
     * 
     * @return A new EncryptedPendingFile instance
     */
    @Override
    protected PendingFile createPendingFile() {
        return new EncryptedPendingFile();
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
     * Create a cached data object for encrypted blocks.
     * 
     * @param key The block hash key
     * @param password The password for decryption
     * @return A new EncryptedCachedData instance
     */
    @Override
    protected CachedData createCacheData(String key, String password) {
        return new EncryptedCachedData(key, password);
    }

    /**
     * Inner class for handling cached encrypted block data.
     * This class manages the decryption and decompression of block data
     * that has been retrieved from storage.
     */
    private class EncryptedCachedData extends CachedData {
        /**
         * The hash identifier of the block.
         */
        private final String hash;
        
        /**
         * List of encrypted data entries within the block.
         */
        private final ArrayList<byte[]> blockEntries;

        /**
         * Constructor that loads and decrypts block data.
         * 
         * @param hash The block hash
         * @param password The password for decryption
         */
        private EncryptedCachedData(String hash, String password) {
            this.hash = hash;
            try {
                blockEntries = new ArrayList<>();

                BackupBlock block = getRepository().block(hash);
                if (block == null) {
                    throw new IOException(String.format("Trying to get unknown block \"%s\"", hash));
                }

                try (ByteArrayInputStream inputStream = new ByteArrayInputStream(getBlockDownloader()
                        .downloadBlock(block, password))) {
                    try (DataInputStream dataInputStream = new DataInputStream(inputStream)) {
                        while (dataInputStream.available() > 0) {
                            int length = dataInputStream.readInt();
                            byte[] data = new byte[length];
                            if (dataInputStream.read(data) != length) {
                                throw new IOException("Unexpected end of file");
                            }
                            blockEntries.add(data);
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
         * @param partHash The hash of the part for decryption
         * @return The decrypted and decompressed data
         * @throws IOException If there's an error decrypting or decompressing
         */
        public byte[] get(int index, String partHash) throws IOException {
            byte[] data = blockEntries.get(index - 1);
            SecretKeySpec secretKeySpec = new SecretKeySpec(Hash.decodeBytes(partHash), KEY_ALGORITHM);
            try {
                Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);

                cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new IvParameterSpec(new byte[IV_SIZE]));
                int size = cipher.getOutputSize(data.length);
                byte[] ret = new byte[size];
                int decodedSize = cipher.doFinal(data, 0, data.length, ret, 0);

                try (ByteArrayInputStream inputStream = new ByteArrayInputStream(ret, 0, decodedSize)) {
                    try (GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
                        return IOUtils.readAllBytes(gzipInputStream);
                    }
                }
            } catch (InvalidAlgorithmParameterException | NoSuchPaddingException | ShortBufferException |
                     IllegalBlockSizeException | NoSuchAlgorithmException | BadPaddingException |
                     InvalidKeyException e) {
                throw new IOException(e);
            }
        }

        /**
         * Check if this cached data equals another object.
         * 
         * @param o The object to compare with
         * @return true if the objects are equal, false otherwise
         */
        @Override
        public boolean equals(Object o) {
            if (o instanceof EncryptedCachedData that) {
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
     * Inner class for handling pending encrypted file data.
     * This class manages the compression and encryption of file data
     * before it is stored in a block.
     */
    private class EncryptedPendingFile extends PendingFile {
        /**
         * Output stream for accumulating encrypted block data.
         */
        private ByteArrayOutputStream output = new ByteArrayOutputStream(getTargetSize());
        
        /**
         * Data output stream for writing structured data to the output stream.
         */
        private DataOutputStream dataOutput = new DataOutputStream(output);

        /**
         * Add a part to the pending file with encryption.
         * 
         * @param index The index of the part
         * @param data The data to add
         * @param partHash The hash of the part for encryption
         * @throws IOException If there's an error compressing or encrypting
         */
        @Override
        protected void addPartData(int index, byte[] data, String partHash) throws IOException {
            try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
                    gzipOutputStream.write(data);
                }
                byte[] compressedData = byteArrayOutputStream.toByteArray();

                SecretKeySpec secretKeySpec = new SecretKeySpec(Hash.decodeBytes(partHash), KEY_ALGORITHM);
                try {
                    Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);

                    cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new IvParameterSpec(new byte[IV_SIZE]));
                    int size = cipher.getOutputSize(compressedData.length);
                    byte[] ret = new byte[size];
                    cipher.doFinal(compressedData, 0, compressedData.length, ret, 0);
                    dataOutput.writeInt(ret.length);
                    dataOutput.write(ret);
                } catch (InvalidAlgorithmParameterException | NoSuchPaddingException | ShortBufferException |
                         IllegalBlockSizeException | NoSuchAlgorithmException | BadPaddingException |
                         InvalidKeyException e) {
                    throw new IOException(e);
                }
            }
        }

        /**
         * Estimate the current size of the pending file.
         * 
         * @return The estimated size in bytes
         */
        @Override
        public synchronized int estimateSize() {
            return output.size();
        }

        /**
         * Get the complete data for the pending file.
         * 
         * @return The complete file data as a byte array
         * @throws IOException If there's an error finalizing the data
         */
        @Override
        public synchronized byte[] data() throws IOException {
            dataOutput.close();
            dataOutput = null;
            output.close();
            byte[] ret = output.toByteArray();
            output = null;
            return ret;
        }
    }
}
