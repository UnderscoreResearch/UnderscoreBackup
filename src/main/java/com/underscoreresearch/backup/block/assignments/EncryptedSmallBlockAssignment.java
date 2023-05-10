package com.underscoreresearch.backup.block.assignments;

import static com.underscoreresearch.backup.block.assignments.EncryptedSmallBlockAssignment.FORMAT;

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

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.block.BlockDownloader;
import com.underscoreresearch.backup.block.BlockFormatPlugin;
import com.underscoreresearch.backup.block.FileBlockExtractor;
import com.underscoreresearch.backup.block.FileBlockUploader;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOUtils;

@Slf4j
@BlockFormatPlugin(FORMAT)
public class EncryptedSmallBlockAssignment extends SmallFileBlockAssignment implements FileBlockExtractor {
    public static final String FORMAT = "ENC";
    private static final String ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int IV_SIZE = 16;

    public EncryptedSmallBlockAssignment(FileBlockUploader uploader,
                                         BlockDownloader blockDownloader,
                                         MetadataRepository repository,
                                         FileSystemAccess access,
                                         EncryptionKey encryptionKey,
                                         int maximumFileSize,
                                         int targetSize) {
        super(uploader, blockDownloader, repository, access, encryptionKey, maximumFileSize, targetSize);
    }

    @Override
    protected PendingFile createPendingFile() {
        return new EncryptedPendingFile();
    }

    @Override
    protected String getFormat() {
        return FORMAT;
    }

    @Override
    protected CachedData createCacheData(String key, String password) {
        return new EncryptedCachedData(key, password);
    }

    private class EncryptedCachedData extends CachedData {
        private static final long MINIMUM_COMPRESSED_SIZE = 8192;
        private static final long MINIMUM_COMPRESSED_RATIO = 2;
        private String hash;
        private ArrayList<byte[]> blockEntries;

        private EncryptedCachedData(String hash, String password) {
            this.hash = hash;
            try {
                blockEntries = new ArrayList<>();
                try (ByteArrayInputStream inputStream = new ByteArrayInputStream(getBlockDownloader()
                        .downloadBlock(hash, password))) {
                    try (DataInputStream dataInputStream = new DataInputStream(inputStream)) {
                        while (dataInputStream.available() > 0) {
                            int length = dataInputStream.readInt();
                            byte[] data = new byte[length];
                            dataInputStream.read(data);
                            blockEntries.add(data);
                        }
                    }
                }
            } catch (IOException exc) {
                throw new RuntimeException(exc);
            }
        }

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


        @Override
        public boolean equals(Object o) {
            EncryptedCachedData that = (EncryptedCachedData) o;
            return Objects.equals(hash, that.hash);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), hash);
        }
    }

    private class EncryptedPendingFile extends PendingFile {
        private ByteArrayOutputStream output = new ByteArrayOutputStream(getTargetSize());
        private DataOutputStream dataOutput = new DataOutputStream(output);

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

        @Override
        public synchronized int estimateSize() {
            return output.size();
        }

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
