package com.underscoreresearch.backup.encryption;

import lombok.Getter;

import java.security.GeneralSecurityException;

/**
 * Interface for public key cryptography methods.
 * Defines operations for key pair generation, secret generation and encapsulation,
 * and secret recreation from encapsulated data.
 */
public interface PublicKeyMethod {

    /**
     * Creates a new key pair.
     *
     * @param privateIdentity The private identity to use for key encryption
     * @return The created public key with encrypted private key
     * @throws GeneralSecurityException If key generation fails
     */
    PublicKey createKeyPair(EncryptionIdentity.PrivateIdentity privateIdentity)
            throws GeneralSecurityException;

    /**
     * Generates a new secret and encapsulates it for the given public key.
     *
     * @param publicKey The public key to use for encapsulation
     * @return The generated secret and its encapsulation
     * @throws GeneralSecurityException If secret generation fails
     */
    GeneratedKey generateNewSecret(PublicKey publicKey)
            throws GeneralSecurityException;

    /**
     * Encapsulates an existing secret for the given public key.
     *
     * @param publicKey The public key to use for encapsulation
     * @param secret The secret to encapsulate
     * @return The encapsulated secret
     * @throws GeneralSecurityException If encapsulation fails
     */
    GeneratedKey encapsulateSecret(PublicKey publicKey, byte[] secret)
            throws GeneralSecurityException;

    /**
     * Recreates a secret from an encapsulated key using a private key.
     *
     * @param privateKey The private key to use for decapsulation
     * @param generatedKey The encapsulated key
     * @return The recreated secret
     * @throws GeneralSecurityException If secret recreation fails
     */
    byte[] recreateSecret(PublicKey.PrivateKey privateKey, EncapsulatedKey generatedKey) throws GeneralSecurityException;

    /**
     * Represents a generated secret and its encapsulation.
     */
    @Getter
    class GeneratedKey extends EncapsulatedKey {
        private final byte[] secret;

        /**
         * Creates a generated key.
         *
         * @param secret The secret bytes
         * @param encapsulation The encapsulation bytes
         */
        public GeneratedKey(byte[] secret, byte[] encapsulation) {
            super(encapsulation);
            this.secret = secret;
        }
    }

    /**
     * Represents an encapsulated key.
     */
    @Getter
    class EncapsulatedKey {
        private final byte[] encapsulation;

        /**
         * Creates an encapsulated key.
         *
         * @param encapsulation The encapsulation bytes
         */
        public EncapsulatedKey(byte[] encapsulation) {
            this.encapsulation = encapsulation;
        }
    }
}
