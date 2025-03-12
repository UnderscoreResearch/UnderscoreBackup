package com.underscoreresearch.backup.encryption.encryptors;

import com.google.common.collect.Sets;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.encryption.PublicKeyMethod;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import lombok.extern.slf4j.Slf4j;

import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Set;

import static com.underscoreresearch.backup.encryption.IdentityKeys.KYBER_KEY;
import static com.underscoreresearch.backup.encryption.IdentityKeys.X25519_KEY;

@Slf4j
public class AesEncryptorPqcStable extends AesEncryptorGcmStable {
    public static final Set<String> KEY_TYPES_PQC = Sets.newHashSet(X25519_KEY, KYBER_KEY);

    protected byte[] createKeySecret(BackupBlockStorage storage, IdentityKeys key) throws GeneralSecurityException {
        IdentityKeys.EncryptionParameters parameters = key.getEncryptionParameters(KEY_TYPES_PQC);
        byte[] combinedKey = parameters.getSecret();

        storage.addProperty(X25519_KEY, Hash.encodeBytes(parameters.getKeys().get(X25519_KEY).getEncapsulation()));
        storage.addProperty(KYBER_KEY, Hash.encodeBytes(parameters.getKeys().get(KYBER_KEY).getEncapsulation()));
        return combinedKey;
    }

    protected byte[] recreateKeySecret(BackupBlockStorage storage, IdentityKeys.PrivateKeys key) throws GeneralSecurityException {
        if (!storage.getProperties().containsKey(KYBER_KEY)) {
            return key.recreateSecret(Map.of(
                    X25519_KEY, new PublicKeyMethod.EncapsulatedKey(Hash.decodeBytes(storage.getProperties().get(X25519_KEY)))));
        }
        return key.recreateSecret(Map.of(
                X25519_KEY, new PublicKeyMethod.EncapsulatedKey(Hash.decodeBytes(storage.getProperties().get(X25519_KEY))),
                KYBER_KEY, new PublicKeyMethod.EncapsulatedKey(Hash.decodeBytes(storage.getProperties().get(KYBER_KEY)))));
    }

    protected IdentityKeys.EncryptionParameters createKeySecret(IdentityKeys key) throws GeneralSecurityException {
        return key.getEncryptionParameters(KEY_TYPES_PQC);
    }
}
