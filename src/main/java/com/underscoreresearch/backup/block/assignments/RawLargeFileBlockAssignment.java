package com.underscoreresearch.backup.block.assignments;

import com.underscoreresearch.backup.block.BlockFormatPlugin;
import com.underscoreresearch.backup.block.FileBlockUploader;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.model.BackupFilePart;

import java.io.IOException;

@BlockFormatPlugin("RAW")
public class RawLargeFileBlockAssignment extends LargeFileBlockAssignment {
    public RawLargeFileBlockAssignment(FileBlockUploader uploader, FileSystemAccess access, int maximumBlockSize) {
        super(uploader, access, maximumBlockSize);
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
