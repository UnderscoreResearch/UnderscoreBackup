package com.underscoreresearch.backup.encryption;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class EncryptionKeyTest {
    @Test
    public void test() {
        EncryptionKey random = EncryptionKey.generateKeys();
        EncryptionKey seeded = EncryptionKey.generateKeyWithPassphrase("seed");

        EncryptionKey randomPublic = random.publicOnly();
        EncryptionKey seededPublic = seeded.publicOnly();

        byte[] randomSecret = EncryptionKey.combinedSecret(random.getPrivateKey(null), seededPublic);
        byte[] seededSecret = EncryptionKey.combinedSecret(seeded.getPrivateKey("seed"), randomPublic);
        assertThat(randomSecret, Is.is(seededSecret));
    }

    @Test
    public void testChangedPassphrase() {
        EncryptionKey seeded1 = EncryptionKey.generateKeyWithPassphrase("seed");
        EncryptionKey seeded2 = EncryptionKey.changeEncryptionPassphrase("seed",
                "another",
                seeded1
        );

        assertThat(seeded1.getPrivateKey("seed").getDisplayPrivateKey(),
                Is.is(seeded2.getPrivateKey("another").getDisplayPrivateKey()));
    }

    @Test
    public void testProperties() {
        EncryptionKey encrypion = EncryptionKey.generateKeys();

        encrypion.getPrivateKey(null).setPrivateKey("ABCDE");
        assertThat(encrypion.getPrivateKey(null).getPrivateKey(), Is.is("ABCDE"));
        encrypion.getPrivateKey(null).setPrivateKey(null);
        assertNull(encrypion.getPrivateKey(null).getPrivateKey());

        encrypion.setPublicKey("ABCDE");
        assertThat(encrypion.getPublicKey(), Is.is("ABCDE"));
        encrypion.setPublicKey(null);
        assertNull(encrypion.getPublicKey());

        encrypion.setSalt("ABCDE");
        assertThat(encrypion.getSalt(), Is.is("ABCDE"));
        encrypion.setSalt(null);
        assertNull(encrypion.getSalt());
    }

    @Test
    public void testJson() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        EncryptionKey key1 = EncryptionKey.generateKeys();
        EncryptionKey key2 = EncryptionKey.generateKeys();

        byte[] secret = EncryptionKey.combinedSecret(key1.getPrivateKey(null), key2);

        key1 = mapper.readValue(mapper.writeValueAsString(key1), EncryptionKey.class);
        key2 = mapper.readValue(mapper.writeValueAsString(key2), EncryptionKey.class);

        assertThat(EncryptionKey.combinedSecret(key1.getPrivateKey(null), key2), Is.is(secret));
    }

    @Test
    public void testJsonSeeded() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        EncryptionKey key1 = EncryptionKey.generateKeyWithPassphrase("abc");
        EncryptionKey key2 = EncryptionKey.generateKeyWithPassphrase("def");

        byte[] secret = EncryptionKey.combinedSecret(key1.getPrivateKey("abc"), key2);

        key1 = mapper.readValue(mapper.writeValueAsString(key1), EncryptionKey.class);
        key2 = mapper.readValue(mapper.writeValueAsString(key2), EncryptionKey.class);

        assertThat(EncryptionKey.combinedSecret(key1.getPrivateKey("abc"), key2), Is.is(secret));
        assertThat(EncryptionKey.combinedSecret(key2.getPrivateKey("def"), key1), Is.is(secret));
    }
}