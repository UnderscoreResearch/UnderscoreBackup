package com.underscoreresearch.backup.block.assignments;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.block.BlockFormatPlugin;
import com.underscoreresearch.backup.block.FileBlockUploader;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupFilePart;

@Slf4j
@BlockFormatPlugin("GZIP")
public class GzipLargeFileBlockAssignment extends LargeFileBlockAssignment {
    public GzipLargeFileBlockAssignment(FileBlockUploader uploader, FileSystemAccess access, int maximumBlockSize) {
        super(uploader, access, maximumBlockSize);
    }

    @Override
    protected byte[] processBuffer(byte[] buffer) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
                gzipOutputStream.write(buffer, 0, buffer.length);
            }
            return outputStream.toByteArray();
        }
    }

    @Override
    public byte[] extractPart(BackupFilePart file, byte[] blockData) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(blockData)) {
            try (GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
                return IOUtils.readAllBytes(gzipInputStream);
            }
        }
    }

    @Override
    protected String getFormat() {
        return "GZIP";
    }
}
