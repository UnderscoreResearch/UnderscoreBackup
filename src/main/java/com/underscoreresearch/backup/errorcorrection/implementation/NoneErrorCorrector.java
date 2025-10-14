package com.underscoreresearch.backup.errorcorrection.implementation;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.errorcorrection.ErrorCorrector;
import com.underscoreresearch.backup.errorcorrection.ErrorCorrectorPlugin;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.underscoreresearch.backup.errorcorrection.implementation.NoneErrorCorrector.NONE;

/**
 * Implementation of ErrorCorrector that provides no actual error correction.
 * This implementation simply splits data into parts without adding redundancy.
 * All parts are required to reconstruct the original data.
 */
@RequiredArgsConstructor
@ErrorCorrectorPlugin(NONE)
public class NoneErrorCorrector implements ErrorCorrector {
    /**
     * Constant for the "NONE" error correction type.
     */
    public static final String NONE = "NONE";
    
    /**
     * The maximum size of each part in bytes.
     */
    private final int maximumPartSize;

    /**
     * Encodes data by splitting it into parts without adding redundancy.
     * If the data is smaller than the maximum part size, it's returned as a single part.
     * Otherwise, it's split into multiple parts of maximum part size.
     *
     * @param storage The backup block storage to update with error correction info
     * @param originalData The original data to encode
     * @return A list of data parts
     */
    @Override
    public List<byte[]> encodeErrorCorrection(BackupBlockStorage storage, byte[] originalData) {
        storage.setEc(NONE);
        if (originalData.length <= maximumPartSize) {
            return Lists.newArrayList(originalData);
        }

        List<byte[]> ret = new ArrayList<>();
        for (int i = 0; i < originalData.length; ) {
            int length = Math.min(originalData.length - i, maximumPartSize);

            ret.add(Arrays.copyOfRange(originalData, i, i + length));
            i += length;
        }

        return ret;
    }

    /**
     * Decodes data by concatenating all parts.
     * All parts must be present for successful decoding.
     *
     * @param storage The backup block storage containing error correction parameters
     * @param parts The list of available parts for decoding
     * @return The decoded original data
     * @throws Exception If any part is missing
     */
    @Override
    public byte[] decodeErrorCorrection(BackupBlockStorage storage, List<byte[]> parts) throws Exception {
        if (parts.size() == 1) {
            return parts.get(0);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (byte[] part : parts)
            if (part != null)
                outputStream.write(part);
            else
                throw new IOException("Missing part of block");

        return outputStream.toByteArray();
    }

    /**
     * Gets the minimum number of parts needed to decode the data.
     * For this implementation, all parts are required.
     *
     * @param storage The backup block storage containing error correction parameters
     * @return The total number of parts
     */
    @Override
    public int getMinimumSufficientParts(BackupBlockStorage storage) {
        return storage.getParts().size();
    }
}
