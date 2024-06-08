package com.underscoreresearch.backup.encryption.encryptors;

import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptionFormatTypes.NON_PADDED_PQC;
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptionFormatTypes.PADDED_PQC;
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptorPqcStable.KEY_TYPES_PQC;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.encryption.PublicKeyMethod;
import com.underscoreresearch.backup.model.BackupBlockStorage;

@Slf4j
public class AesEncryptorPqc extends AesEncryptorGcm {
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<Map<String, String>>() {
    };
    private static final ObjectReader ENCAPSULATION_READER = MAPPER.readerFor(MAP_TYPE);
    private static final ObjectWriter ENCAPSULATION_WRITER = MAPPER.writerFor(MAP_TYPE);

    @Override
    protected byte paddingFormat(int estimatedSize) {
        return estimatedSize % 4 != 3 ? NON_PADDED_PQC : PADDED_PQC;
    }

    @Override
    protected int adjustEstimatedSize(byte paddingFormat, int estimatedSize) {
        switch (paddingFormat) {
            case NON_PADDED_PQC -> {
                return estimatedSize;
            }
            case PADDED_PQC -> {
                return estimatedSize + 1;
            }
        }
        throw new IllegalArgumentException("Unknown AES padding format");
    }

    @Override
    protected int adjustDecodeLength(byte paddingFormat, int payloadLength) {
        switch (paddingFormat) {
            case NON_PADDED_PQC -> {
                return payloadLength;
            }
            case PADDED_PQC -> {
                return payloadLength - 1;
            }
        }
        throw new IllegalArgumentException("Unknown AES padding format");
    }

    @Override
    protected IdentityKeys.EncryptionParameters createKeySecret(IdentityKeys key) throws GeneralSecurityException {
        return key.getEncryptionParameters(KEY_TYPES_PQC);
    }

    @Override
    protected byte[] getKeyEncapsulationData(BackupBlockStorage storage, IdentityKeys.EncryptionParameters parameters) {
        Map<String, String> encapsulatedData = parameters.getKeys().entrySet().stream()
                .map(entry -> {
                    // We use different encodings in the storage and the embedded encapsulation. This is intentional
                    // because it is not compressed in the embedded version and we want to be backwards compatible
                    // in the storage version.
                    if (storage != null)
                        storage.getProperties().put(entry.getKey(),
                                Hash.encodeBytes(entry.getValue().getEncapsulation()));
                    return Map.entry(entry.getKey(), Hash.encodeBytes64(entry.getValue().getEncapsulation()));
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        try {
            byte[] data = ENCAPSULATION_WRITER.writeValueAsBytes(encapsulatedData);
            int length = data.length;
            int pad = (4 - length % 4) % 4;
            length += pad;

            ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + length);
            buffer.putInt(length);
            buffer.put(data);

            // This has to be evenly divided by 4, or it would mess things up, so we add spaces if needed.
            for (int i = 0; i < pad; i++) {
                buffer.put((byte) 32);
            }
            return buffer.array();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Map<String, PublicKeyMethod.EncapsulatedKey> extractKeyEncapsulation(BackupBlockStorage storage,
                                                                                   byte[] encryptedData,
                                                                                   AtomicInteger currentOffset)
            throws GeneralSecurityException {
        Map<String, PublicKeyMethod.EncapsulatedKey> ret;
        ByteBuffer buffer = ByteBuffer.wrap(encryptedData, currentOffset.get(), Integer.BYTES);
        int length = buffer.getInt();
        currentOffset.addAndGet(Integer.BYTES);
        if (storage != null && storage.getProperties() != null) {
            ret = new HashMap<>();
            storage.getProperties().forEach((key, value) -> ret.put(key,
                    new PublicKeyMethod.EncapsulatedKey(Hash.decodeBytes(value))));
        } else {
            try {
                Map<String, String> data = ENCAPSULATION_READER.readValue(encryptedData, currentOffset.get(), length);
                ret = data.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                        (entry) -> new PublicKeyMethod.EncapsulatedKey(Hash.decodeBytes64(entry.getValue()))));
            } catch (IOException e) {
                throw new GeneralSecurityException(e);
            }
        }

        currentOffset.addAndGet(length);

        return ret;
    }
}
