package com.underscoreresearch.backup.encryption.encryptors.x25519;

import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.PublicKey;
import com.underscoreresearch.backup.encryption.PublicKeyMethod;
import com.underscoreresearch.backup.encryption.encryptors.BaseAesEncryptor;
import com.underscoreresearch.backup.encryption.encryptors.kyber.KyberKeyMethod;

import java.security.GeneralSecurityException;

import static com.underscoreresearch.backup.encryption.IdentityKeys.SYMMETRIC_KEY_SIZE;

/**
 * Implementation of the PublicKeyMethod interface using the X25519 elliptic curve.
 * This class provides methods for creating key pairs, generating and encapsulating secrets,
 * and recreating secrets from encapsulated data using the X25519 algorithm.
 */
public class X25519KeyMethod implements PublicKeyMethod {
    private static final int KEY_SIZE = 32;

    /**
     * Creates a new X25519 key pair.
     *
     * @param privateIdentity The private identity to use for key encryption
     * @return The created public key with encrypted private key
     * @throws GeneralSecurityException If key generation fails
     */
    @Override
    public PublicKey createKeyPair(EncryptionIdentity.PrivateIdentity privateIdentity)
            throws GeneralSecurityException {
        byte[] pk = X25519.generatePrivateKey();
        byte[] pub = X25519.publicFromPrivate(pk);
        return new PublicKey(pub, pk, privateIdentity);
    }

    /**
     * Generates a new secret and encapsulates it for the given public key.
     * Uses X25519 key agreement to create a shared secret.
     *
     * @param publicKey The public key to use for encapsulation
     * @return The generated secret and its encapsulation
     * @throws GeneralSecurityException If secret generation fails
     */
    @Override
    public GeneratedKey generateNewSecret(PublicKey publicKey) throws GeneralSecurityException {
        byte[] pk = X25519.generatePrivateKey();
        byte[] pub = X25519.publicFromPrivate(pk);

        byte[] secret = X25519.computeSharedSecret(pk, publicKey.getPublicKey());

        return new GeneratedKey(secret, pub);
    }

    /**
     * Encapsulates an existing secret for the given public key.
     * Delegates to the KyberKeyMethod for encapsulation.
     *
     * @param publicKey The public key to use for encapsulation
     * @param secret The secret to encapsulate
     * @return The encapsulated secret
     * @throws GeneralSecurityException If encapsulation fails
     */
    @Override
    public GeneratedKey encapsulateSecret(PublicKey publicKey, byte[] secret) throws GeneralSecurityException {
        return KyberKeyMethod.encapsulateSecret(this, publicKey, secret);
    }

    /**
     * Recreates a secret from an encapsulated key using a private key.
     * Uses X25519 key agreement to recreate the shared secret.
     *
     * @param privateKey The private key to use for decapsulation
     * @param generatedKey The encapsulated key
     * @return The recreated secret
     * @throws GeneralSecurityException If secret recreation fails
     * @throws IllegalArgumentException If the encapsulation length is invalid
     */
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
