package com.underscoreresearch.backup.block.assignments;

import static com.underscoreresearch.backup.block.assignments.ZipSmallBlockAssignment.FORMAT;

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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.block.BlockDownloader;
import com.underscoreresearch.backup.block.BlockFormatPlugin;
import com.underscoreresearch.backup.block.FileBlockExtractor;
import com.underscoreresearch.backup.block.FileBlockUploader;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupBlock;

@Slf4j
@BlockFormatPlugin(FORMAT)
public class ZipSmallBlockAssignment extends SmallFileBlockAssignment implements FileBlockExtractor {
    public static final String FORMAT = "ZIP";

    public ZipSmallBlockAssignment(FileBlockUploader uploader,
                                   BlockDownloader blockDownloader,
                                   MetadataRepository repository,
                                   FileSystemAccess access,
                                   EncryptionIdentity encryptionIdentity,
                                   int maximumFileSize,
                                   int targetSize) {
        super(uploader, blockDownloader, repository, access, encryptionIdentity, maximumFileSize, targetSize);
    }

    @Override
    protected PendingFile createPendingFile() {
        return new ZipPendingFile();
    }

    @Override
    protected String getFormat() {
        return FORMAT;
    }

    @Override
    protected CachedData createCacheData(String key, String password) {
        return new ZipCachedData(key, password);
    }

    @Data
    @AllArgsConstructor
    private static class CacheEntry {
        private boolean compressed;
        private byte[] data;

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

    private class ZipCachedData extends CachedData {
        private static final long MINIMUM_COMPRESSED_SIZE = 8192;
        private static final long MINIMUM_COMPRESSED_RATIO = 2;
        private final String hash;
        private final Map<String, CacheEntry> blockEntries;

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

        public byte[] get(int index, String partHash) throws IOException {
            CacheEntry entry = blockEntries.get(String.valueOf(index));
            if (entry != null) {
                return entry.get();
            }
            return null;
        }

        @Override
        public boolean equals(Object o) {
            ZipCachedData that = (ZipCachedData) o;
            return Objects.equals(hash, that.hash);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), hash);
        }
    }

    private class ZipPendingFile extends PendingFile {
        private ByteArrayOutputStream output = new ByteArrayOutputStream(getTargetSize());
        private ZipOutputStream zipOutputStream = new ZipOutputStream(output);

        @Override
        protected void addPartData(int index, byte[] data, String partHash) throws IOException {
            ZipEntry entry = new ZipEntry(String.valueOf(index));
            zipOutputStream.putNextEntry(entry);
            zipOutputStream.write(data, 0, data.length);
            zipOutputStream.closeEntry();
            zipOutputStream.flush();
        }

        @Override
        public synchronized int estimateSize() {
            return output.size();
        }

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
