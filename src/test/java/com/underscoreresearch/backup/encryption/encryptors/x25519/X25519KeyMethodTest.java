package com.underscoreresearch.backup.encryption.encryptors.x25519;

import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.PublicKey;
import com.underscoreresearch.backup.encryption.PublicKeyMethod;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;

import static org.hamcrest.MatcherAssert.assertThat;

class X25519KeyMethodTest {
    @Test
    public void generateAndTest() throws GeneralSecurityException {
        X25519KeyMethod keyMethod = new X25519KeyMethod();
        EncryptionIdentity encryptionIdentity = EncryptionIdentity.generateKeyWithPassword("password");
        EncryptionIdentity.PrivateIdentity privateIdentity = encryptionIdentity.getPrivateIdentity("password");
        PublicKey bob = keyMethod.createKeyPair(privateIdentity);
        PublicKeyMethod.GeneratedKey key = keyMethod.generateNewSecret(bob);
        byte[] secret = keyMethod.recreateSecret(bob.getPrivateKey(privateIdentity), key);

        assertThat(key.getSecret(), Is.is(secret));
    }

    @Test
    public void encapsulateSecret() throws GeneralSecurityException {
        X25519KeyMethod keyMethod = new X25519KeyMethod();
        EncryptionIdentity encryptionIdentity = EncryptionIdentity.generateKeyWithPassword("password");
        EncryptionIdentity.PrivateIdentity privateIdentity = encryptionIdentity.getPrivateIdentity("password");
        PublicKey bob = keyMethod.createKeyPair(privateIdentity);
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
        PublicKeyMethod.EncapsulatedKey encapsulatedKey = keyMethod.encapsulateSecret(bob, secret);
        byte[] recreated = keyMethod.recreateSecret(bob.getPrivateKey(privateIdentity), encapsulatedKey);

        assertThat(recreated, Is.is(secret));
    }
}