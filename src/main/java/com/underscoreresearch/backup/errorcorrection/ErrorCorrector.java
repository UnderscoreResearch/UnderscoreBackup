package com.underscoreresearch.backup.errorcorrection;

import com.underscoreresearch.backup.model.BackupBlockStorage;

import java.util.List;

/**
 * Interface for error correction implementations.
 * Error correctors provide methods to encode data with redundancy
 * and decode it even when some parts are missing or corrupted.
 */
public interface ErrorCorrector {
    /**
     * Encodes data with error correction.
     *
     * @param storage The backup block storage containing error correction parameters
     * @param originalData The original data to encode
     * @return A list of byte arrays representing the encoded parts
     * @throws Exception If there's an error during encoding
     */
    List<byte[]> encodeErrorCorrection(BackupBlockStorage storage, byte[] originalData) throws Exception;

    /**
     * Decodes data from error correction parts.
     *
     * @param storage The backup block storage containing error correction parameters
     * @param parts The list of available parts for decoding
     * @return The decoded original data
     * @throws Exception If there's an error during decoding
     */
    byte[] decodeErrorCorrection(BackupBlockStorage storage, List<byte[]> parts) throws Exception;

    /**
     * Gets the minimum number of parts needed to successfully decode the data.
     *
     * @param storage The backup block storage containing error correction parameters
     * @return The minimum number of parts needed
     */
    int getMinimumSufficientParts(BackupBlockStorage storage);
}
