package com.underscoreresearch.backup.errorcorrection;

import com.underscoreresearch.backup.model.BackupBlockStorage;

import java.util.List;

public interface ErrorCorrector {
    List<byte[]> encodeErrorCorrection(BackupBlockStorage storage, byte[] originalData) throws Exception;

    byte[] decodeErrorCorrection(BackupBlockStorage storage, List<byte[]> parts) throws Exception;

    int getMinimumSufficientParts(BackupBlockStorage storage);
}
