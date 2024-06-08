package com.underscoreresearch.backup.encryption.encryptors.kyber;

import static org.hamcrest.MatcherAssert.assertThat;

import java.security.GeneralSecurityException;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.PublicKey;
import com.underscoreresearch.backup.encryption.PublicKeyMethod;

class KyberKeyMethodTest {
    @Test
    public void generateAndTest() throws GeneralSecurityException {
        KyberKeyMethod kyberKeyMethod = new KyberKeyMethod();
        EncryptionIdentity encryptionIdentity = EncryptionIdentity.generateKeyWithPassword("password");
        EncryptionIdentity.PrivateIdentity privateIdentity = encryptionIdentity.getPrivateIdentity("password");
        PublicKey bob = kyberKeyMethod.createKeyPair(privateIdentity);
        PublicKeyMethod.GeneratedKey key = kyberKeyMethod.generateNewSecret(bob);
        byte[] secret = kyberKeyMethod.recreateSecret(bob.getPrivateKey(privateIdentity), key);

        assertThat(key.getSecret(), Is.is(secret));
    }

    @Test
    public void encapsulateSecret() throws GeneralSecurityException {
        KyberKeyMethod kyberKeyMethod = new KyberKeyMethod();
        EncryptionIdentity encryptionIdentity = EncryptionIdentity.generateKeyWithPassword("password");
        EncryptionIdentity.PrivateIdentity privateIdentity = encryptionIdentity.getPrivateIdentity("password");
        PublicKey bob = kyberKeyMethod.createKeyPair(privateIdentity);
        byte[] secret = new byte[]{
                1,
                2,
                3,
                4,
                5,
                6,
                7,
                8,
                9,
                10,
                11,
                12,
                13,
                14,
                15,
                16,
                17,
                18,
                19,
                20,
                21,
                22,
                23,
                24,
                25,
                26,
                27,
                28,
                29,
                30,
                31,
                32
        };
        PublicKeyMethod.EncapsulatedKey encapsulatedKey = kyberKeyMethod.encapsulateSecret(bob, secret);
        byte[] recreated = kyberKeyMethod.recreateSecret(bob.getPrivateKey(privateIdentity), encapsulatedKey);

        assertThat(recreated, Is.is(secret));
    }
}