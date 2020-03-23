package com.underscoreresearch.backup.encryption;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

class PublicKeyEncrypionTest {
    @Test
    public void test() {
        PublicKeyEncrypion random = PublicKeyEncrypion.generateKeys();
        PublicKeyEncrypion seeded = PublicKeyEncrypion.generateKeyWithSeed("seed", null);

        PublicKeyEncrypion randomPublic = random.publicOnly();
        PublicKeyEncrypion seededPublic = seeded.publicOnly();

        byte[] randomSecret = PublicKeyEncrypion.combinedSecret(random, seededPublic);
        byte[] seededSecret = PublicKeyEncrypion.combinedSecret(seeded, randomPublic);
        assertThat(randomSecret, Is.is(seededSecret));
    }

    @Test
    public void testSeeded() {
        PublicKeyEncrypion seeded1 = PublicKeyEncrypion.generateKeyWithSeed("seed", null);
        PublicKeyEncrypion seeded2 = PublicKeyEncrypion.generateKeyWithSeed("seed", seeded1.getSalt());

        assertThat(seeded1.getPrivateKey(), Is.is(seeded2.getPrivateKey()));
        assertThat(seeded1.getPublicKey(), Is.is(seeded2.getPublicKey()));
    }

    @Test
    public void testProperties() {
        PublicKeyEncrypion encrypion = new PublicKeyEncrypion();

        encrypion.setPrivateKey("ABCDE");
        assertThat(encrypion.getPrivateKey(), Is.is("ABCDE"));
        encrypion.setPrivateKey(null);
        assertNull(encrypion.getPrivateKey());

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
        PublicKeyEncrypion key1 = PublicKeyEncrypion.generateKeys();
        PublicKeyEncrypion key2 = PublicKeyEncrypion.generateKeys().publicOnly();

        byte[] secret = PublicKeyEncrypion.combinedSecret(key1, key2);

        key1 = mapper.readValue(mapper.writeValueAsString(key1), PublicKeyEncrypion.class);
        key2 = mapper.readValue(mapper.writeValueAsString(key2), PublicKeyEncrypion.class);

        assertThat(PublicKeyEncrypion.combinedSecret(key1, key2), Is.is(secret));
    }
}