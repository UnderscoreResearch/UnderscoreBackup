package com.underscoreresearch.backup.encryption;

import static com.underscoreresearch.backup.utils.SerializationUtils.ENCRYPTION_KEY_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.ENCRYPTION_KEY_WRITER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.underscoreresearch.backup.manifest.ManifestManager;

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
    public void testChangedPassphrase() throws JsonProcessingException {
        EncryptionKey seeded1 = EncryptionKey.generateKeyWithPassphrase("seed");
        EncryptionKey seeded2 = EncryptionKey.changeEncryptionPassphrase("seed",
                "another",
                seeded1
        );

        assertThat(seeded1.getPrivateKey("seed").getDisplayPrivateKey(),
                Is.is(seeded2.getPrivateKey("another").getDisplayPrivateKey()));

        String publicOnlyKey = ENCRYPTION_KEY_WRITER.writeValueAsString(seeded2.publicOnly());
        EncryptionKey rebuilt = ENCRYPTION_KEY_READER.readValue(publicOnlyKey);
        assertThat(seeded1.getPrivateKey("seed").getDisplayPrivateKey(),
                Is.is(rebuilt.getPrivateKey("another").getDisplayPrivateKey()));
    }

    @Test
    public void testAdditionalKey() throws IOException {
        EncryptionKey seeded1 = EncryptionKey.generateKeyWithPassphrase("seed");
        EncryptionKey additionalKey = EncryptionKey.generateKeys();
        seeded1.getPrivateKey("seed").getAdditionalKeyManager().addNewKey(additionalKey, Mockito.mock(ManifestManager.class));

        EncryptionKey seeded2 = EncryptionKey.changeEncryptionPassphrase("seed",
                "another",
                seeded1
        );


        String publicOnlyKey = ENCRYPTION_KEY_WRITER.writeValueAsString(seeded2.publicOnly());
        EncryptionKey rebuilt = ENCRYPTION_KEY_READER.readValue(publicOnlyKey);

        String seeded1Private = seeded1.getPrivateKey("seed").getAdditionalKeyManager().findMatchingPrivateKey(additionalKey).getPrivateKey(null).getDisplayPrivateKey();
        String seeded2Private = seeded2.getPrivateKey("another").getAdditionalKeyManager().findMatchingPrivateKey(additionalKey).getPrivateKey(null).getDisplayPrivateKey();
        String rebuildPrivate = rebuilt.getPrivateKey("another").getAdditionalKeyManager().findMatchingPrivateKey(additionalKey).getPrivateKey(null).getDisplayPrivateKey();

        assertThat(seeded1Private, Is.is(seeded2Private));
        assertThat(seeded1Private, Is.is(rebuildPrivate));
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