package com.underscoreresearch.backup.encryption;

import java.security.GeneralSecurityException;

import lombok.Getter;

public interface PublicKeyMethod {

    PublicKey createKeyPair(EncryptionIdentity.PrivateIdentity privateIdentity)
            throws GeneralSecurityException;

    GeneratedKey generateNewSecret(PublicKey publicKey)
            throws GeneralSecurityException;

    GeneratedKey encapsulateSecret(PublicKey publicKey, byte[] secret)
            throws GeneralSecurityException;

    byte[] recreateSecret(PublicKey.PrivateKey privateKey, EncapsulatedKey generatedKey) throws GeneralSecurityException;

    @Getter
    class GeneratedKey extends EncapsulatedKey {
        private final byte[] secret;

        public GeneratedKey(byte[] secret, byte[] encapsulation) {
            super(encapsulation);
            this.secret = secret;
        }
    }

    @Getter
    class EncapsulatedKey {
        private final byte[] encapsulation;

        public EncapsulatedKey(byte[] encapsulation) {
            this.encapsulation = encapsulation;
        }
    }
}
