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

@RequiredArgsConstructor
@ErrorCorrectorPlugin(NONE)
public class NoneErrorCorrector implements ErrorCorrector {
    public static final String NONE = "NONE";
    private final int maximumPartSize;

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

    @Override
    public int getMinimumSufficientParts(BackupBlockStorage storage) {
        return storage.getParts().size();
    }
}
