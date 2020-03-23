package com.underscoreresearch.backup.errorcorrection.implementation;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.errorcorrection.ErrorCorrector;
import com.underscoreresearch.backup.errorcorrection.ErrorCorrectorPlugin;
import com.underscoreresearch.backup.errorcorrection.implementation.reedsolomon.ReedSolomon;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.zip.CRC32;

@ErrorCorrectorPlugin("RS")
@Slf4j
public class ReedSolomonErrorCorrector implements ErrorCorrector {
    private static final int ECC_SIZE = 8;
    private static final String DATA_SHARDS = "d";
    private static final String EC_LENGTH = "l";
    private final int dataShards;
    private final int parityShards;
    private final int totalShards;

    public ReedSolomonErrorCorrector(int dataParts, int parityParts) {
        this.dataShards = dataParts;
        this.parityShards = parityParts;

        this.totalShards = dataParts + parityParts;
    }

    public List<byte[]> encodeErrorCorrection(BackupBlockStorage storage, byte[] originalData)
            throws Exception {
        if (storage.getProperties() == null)
            storage.setProperties(new HashMap<>());
        storage.getProperties().put(DATA_SHARDS, Integer.toString(dataShards));
        storage.setEc("RS");
        int shardSize = (originalData.length + dataShards - 1) / dataShards;

        byte[][] shards = new byte[totalShards][shardSize + ECC_SIZE];

        for (int i = 0; i < dataShards; i++) {
            int length = shardSize;
            if ((i + 1) * shardSize > originalData.length)
                length = originalData.length - i * shardSize;
            if (length > 0) {
                System.arraycopy(originalData, i * shardSize, shards[i], 0, length);
            }
        }
        if (originalData.length != shardSize * dataShards)
            storage.getProperties().put(EC_LENGTH, Integer.toString(originalData.length));

        ReedSolomon reedSolomon = ReedSolomon.create(dataShards, parityShards);
        reedSolomon.encodeParity(shards, 0, shardSize);

        for (int i = 0; i < totalShards; i++) {
            CRC32 crc32 = new CRC32();
            crc32.update(shards[i], 0, shardSize);
            crc32.getValue();

            System.arraycopy(ByteBuffer.allocate(ECC_SIZE).putLong(crc32.getValue()).array(), 0,
                    shards[i], shardSize, ECC_SIZE);
        }

        return Lists.newArrayList(shards);
    }

    public byte[] decodeErrorCorrection(BackupBlockStorage storage, List<byte[]> parts)
            throws Exception {
        int decodeTotalShards = parts.size();
        int decodeDataShards = Integer.parseInt(storage.getProperties().get(DATA_SHARDS));

        final byte[][] shards = new byte[decodeTotalShards][];
        final boolean[] shardPresent = new boolean[decodeTotalShards];
        int shardSize = 0;
        int shardCount = 0;
        for (int i = 0; i < parts.size(); i++) {
            byte[] part = parts.get(i);
            if (part != null) {
                CRC32 crc32 = new CRC32();
                crc32.update(part, 0, part.length - ECC_SIZE);
                crc32.getValue();

                if (crc32.getValue() == ByteBuffer.wrap(part, part.length - ECC_SIZE, ECC_SIZE).getLong()) {
                    if (shardSize != 0 && shardSize + ECC_SIZE != part.length)
                        throw new IOException("Inconsistent part lengths");
                    else
                        shardSize = part.length - ECC_SIZE;
                    shardPresent[i] = true;
                    shards[i] = part;
                    shardCount++;
                } else {
                    log.warn("Wrong CRC32 on RS part {}, discarding", i);
                }
            }
        }

        if (shardCount < decodeDataShards) {
            throw new IOException(decodeDataShards + " shards needed, only " + shardCount + " exists");
        }

        for (int i = 0; i < decodeTotalShards; i++) {
            if (shards[i] == null) {
                shards[i] = new byte[shardSize + ECC_SIZE];
            }
        }

        ReedSolomon reedSolomon = ReedSolomon.create(decodeDataShards, decodeTotalShards - decodeDataShards);
        reedSolomon.decodeMissing(shards, shardPresent, 0, shardSize);

        String ecLength = storage.getProperties().get(EC_LENGTH);
        int decodeEcLength;
        if (ecLength != null) {
            decodeEcLength = Integer.parseInt(ecLength);
        } else {
            decodeEcLength = shardSize * decodeDataShards;
        }

        byte[] allBytes = new byte[decodeEcLength];
        for (int i = 0; i < decodeDataShards; i++) {
            int offset = shardSize * i;
            int length = Math.min(decodeEcLength - offset, shardSize);
            if (length > 0) {
                System.arraycopy(shards[i], 0, allBytes, shardSize * i, length);
            }
        }

        return allBytes;
    }

    @Override
    public int getMinimumSufficientParts(BackupBlockStorage storage) {
        return Integer.parseInt(storage.getProperties().get(DATA_SHARDS));
    }
}
