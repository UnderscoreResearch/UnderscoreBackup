package com.underscoreresearch.backup.encryption.encryptors.x25519;

import static com.underscoreresearch.backup.encryption.IdentityKeys.SYMMETRIC_KEY_SIZE;

import java.security.GeneralSecurityException;

import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.PublicKey;
import com.underscoreresearch.backup.encryption.PublicKeyMethod;
import com.underscoreresearch.backup.encryption.encryptors.BaseAesEncryptor;
import com.underscoreresearch.backup.encryption.encryptors.kyber.KyberKeyMethod;

public class X25519KeyMethod implements PublicKeyMethod {
    private static final int KEY_SIZE = 32;

    @Override
    public PublicKey createKeyPair(EncryptionIdentity.PrivateIdentity privateIdentity)
            throws GeneralSecurityException {
        byte[] pk = X25519.generatePrivateKey();
        byte[] pub = X25519.publicFromPrivate(pk);
        return new PublicKey(pub, pk, privateIdentity);
    }

    @Override
    public GeneratedKey generateNewSecret(PublicKey publicKey) throws GeneralSecurityException {
        byte[] pk = X25519.generatePrivateKey();
        byte[] pub = X25519.publicFromPrivate(pk);

        byte[] secret = X25519.computeSharedSecret(pk, publicKey.getPublicKey());

        return new GeneratedKey(secret, pub);
    }

    @Override
    public GeneratedKey encapsulateSecret(PublicKey publicKey, byte[] secret) throws GeneralSecurityException {
        return KyberKeyMethod.encapsulateSecret(this, publicKey, secret);
    }

    @Override
    public byte[] recreateSecret(PublicKey.PrivateKey privateKey, EncapsulatedKey generatedKey) throws GeneralSecurityException {
        if (generatedKey.getEncapsulation().length == KEY_SIZE) {
            return X25519.computeSharedSecret(privateKey.getPrivateKey(), generatedKey.getEncapsulation());
        }
        if (generatedKey.getEncapsulation().length == KEY_SIZE + SYMMETRIC_KEY_SIZE) {
            byte[] keyData = new byte[SYMMETRIC_KEY_SIZE];
            byte[] publicKey = new byte[KEY_SIZE];
            System.arraycopy(generatedKey.getEncapsulation(), 0, keyData, 0, keyData.length);
            System.arraycopy(generatedKey.getEncapsulation(), keyData.length, publicKey, 0, publicKey.length);
            byte[] key = X25519.computeSharedSecret(privateKey.getPrivateKey(), publicKey);
            return BaseAesEncryptor.applyKeyData(key, keyData);
        }
        throw new IllegalArgumentException("Invalid encapsulation length");
    }
}
