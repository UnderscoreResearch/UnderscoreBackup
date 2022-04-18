package com.underscoreresearch.backup.block.assignments;

import java.io.IOException;

import com.underscoreresearch.backup.block.BlockDownloader;
import com.underscoreresearch.backup.block.BlockFormatPlugin;
import com.underscoreresearch.backup.block.FileBlockUploader;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.model.BackupFilePart;

@BlockFormatPlugin("RAW")
public class RawLargeFileBlockAssignment extends LargeFileBlockAssignment {
    public RawLargeFileBlockAssignment(FileBlockUploader uploader, BlockDownloader downloader, FileSystemAccess access,
                                       MetadataRepository metadataRepository, int maximumBlockSize) {
        super(uploader, downloader, access, metadataRepository, maximumBlockSize);
    }

    protected byte[] processBuffer(byte[] buffer) throws IOException {
        return buffer;
    }

    protected String getFormat() {
        return "RAW";
    }

    @Override
    public byte[] extractPart(BackupFilePart file, byte[] blockData) throws IOException {
        return blockData;
    }

    @Override
    public boolean shouldCache() {
        return false;
    }
}
