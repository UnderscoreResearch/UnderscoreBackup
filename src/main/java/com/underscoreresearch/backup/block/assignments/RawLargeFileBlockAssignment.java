package com.underscoreresearch.backup.block.assignments;

import com.underscoreresearch.backup.block.BlockDownloader;
import com.underscoreresearch.backup.block.BlockFormatPlugin;
import com.underscoreresearch.backup.block.FileBlockUploader;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.utils.state.MachineState;

import java.io.IOException;

@BlockFormatPlugin("RAW")
public class RawLargeFileBlockAssignment extends LargeFileBlockAssignment {
    public RawLargeFileBlockAssignment(FileBlockUploader uploader, BlockDownloader downloader, FileSystemAccess access,
                                       MetadataRepository metadataRepository, MachineState machineState,
                                       EncryptionIdentity encryptionIdentity, int maximumBlockSize) {
        super(uploader, downloader, access, metadataRepository, machineState, encryptionIdentity, maximumBlockSize);
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
}
