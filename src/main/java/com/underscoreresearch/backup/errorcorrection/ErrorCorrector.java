package com.underscoreresearch.backup.errorcorrection;

import java.util.List;

import com.underscoreresearch.backup.model.BackupBlockStorage;

public interface ErrorCorrector {
    List<byte[]> encodeErrorCorrection(BackupBlockStorage storage, byte[] originalData) throws Exception;

    byte[] decodeErrorCorrection(BackupBlockStorage storage, List<byte[]> parts) throws Exception;

    int getMinimumSufficientParts(BackupBlockStorage storage);
}
