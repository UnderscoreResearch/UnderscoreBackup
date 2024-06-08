package com.underscoreresearch.backup.encryption.encryptors;

import static com.underscoreresearch.backup.encryption.IdentityKeys.KYBER_KEY;
import static com.underscoreresearch.backup.encryption.IdentityKeys.X25519_KEY;
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptorPqcStable.KEY_TYPES_PQC;
import static com.underscoreresearch.backup.encryption.encryptors.PQCEncryptor.PQC_ENCRYPTION;

import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import com.google.inject.Inject;
import com.underscoreresearch.backup.encryption.EncryptorPlugin;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.encryption.PublicKeyMethod;
import com.underscoreresearch.backup.model.BackupBlockStorage;

/**
 * X25519 encryptor. Called AES for historical reasons (AES is used for the symmetrical cypher)
 * <p>
 * So this format is a bit of a mess in that I started using CBC encoding and padding and then realized that I really
 * should be using GCM encoding. Unfortunately I left no field for future expansion in the original format but I have
 * figured out a way to be backwards compatible and add future extensibility in case I want to change this again
 * in the future. So here is how the payload works.
 * <p>
 * First byte is a padding version indicator which can currently be 0 for CBC, 1 for GCM and 2 for GCM with a single
 * additional byte for padding its payload to an even length. This byte is missing for all legacy data created before
 * the introduction of the GCM encoding. However, any encrypted block with an even number of bytes in length will
 * assumed to be of CBC encoding. This is also why the GCM encoding needs format bytes since it can be of uneven size.
 * <p>
 * The next 12 bytes for GCM and 16 bytes for CBC contain the IV vector for the crypto.
 * <p>
 * The next 32 bytes contain the public key used to combine with the private key to create the key used for the AES256
 * algorithm.
 * <p>
 * The entire rest of the data is the encryption payload.
 * <p>
 * There is also another format used when storage is specified by default. In this format only the first byte is used
 * to specify the format and the entire rest of the payload is the encryption. The IV in this case is a 0 array, the
 * encryption key is the SHA3-256 of the payload (Which is different from the SHA-256 used to create the block ID. In
 * this format a block with the same contents will always be encrypted to exactly the same encryption payload allowing
 * for good deduplication of the data without jeopardizing the contents.
 */
@EncryptorPlugin(PQC_ENCRYPTION)
@Slf4j
public class PQCEncryptor extends BaseAesEncryptor {
    public static final String PQC_ENCRYPTION = "PQC";

    private static final AesEncryptorFormat stableFormat = new AesEncryptorPqcStable();
    private static final AesEncryptorFormat defaultFormat = new AesEncryptorPqc();

    @Inject
    public PQCEncryptor() {
    }

    @Override
    protected void storeEncryptionParameters(BackupBlockStorage storage, IdentityKeys.EncryptionParameters ret) {
        storage.getProperties().put(X25519_KEY, Hash.encodeBytes(ret.getKeys().get(X25519_KEY).getEncapsulation()));
        storage.getProperties().put(KYBER_KEY, Hash.encodeBytes(ret.getKeys().get(KYBER_KEY).getEncapsulation()));
    }

    @Override
    protected Set<String> getEncryptionKeys() {
        return KEY_TYPES_PQC;
    }

    @Override
    protected Map<String, PublicKeyMethod.EncapsulatedKey> createEncapsulatedKeys(BackupBlockStorage storage) {
        PublicKeyMethod.EncapsulatedKey x25519PK = new PublicKeyMethod.EncapsulatedKey(Hash.decodeBytes(storage.getProperties().get(X25519_KEY)));
        if (storage.getProperties().containsKey(KYBER_KEY)) {
            PublicKeyMethod.EncapsulatedKey kyberPK = new PublicKeyMethod.EncapsulatedKey(Hash.decodeBytes(storage.getProperties().get(KYBER_KEY)));
            return Map.of(
                    X25519_KEY, x25519PK,
                    KYBER_KEY, kyberPK
            );
        }
        return Map.of(
                X25519_KEY, x25519PK
        );
    }

    @Override
    protected AesEncryptorFormat getEncryptorFormat(byte[] data) {
        if (data.length % 4 == 0) {
            return super.getEncryptorFormat(data);
        }

        return switch (data[0]) {
            case AesEncryptionFormatTypes.NON_PADDED_PQC,
                 AesEncryptionFormatTypes.PADDED_PQC -> defaultFormat;
            case AesEncryptionFormatTypes.NON_PADDED_GCM_STABLE,
                 AesEncryptionFormatTypes.PADDED_GCM_STABLE -> stableFormat;
            default -> super.getEncryptorFormat(data);
        };
    }

    @Override
    public byte[] encryptBlock(BackupBlockStorage storage, byte[] data, IdentityKeys key) throws GeneralSecurityException {
        if (storage != null && isStableDedupe()) {
            return stableFormat.encryptBlock(storage, data, key);
        }
        return defaultFormat.encryptBlock(storage, data, key);
    }
}