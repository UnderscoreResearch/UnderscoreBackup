package com.underscoreresearch.backup.encryption;

import static com.underscoreresearch.backup.utils.SerializationUtils.ENCRYPTION_KEY_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.ENCRYPTION_KEY_WRITER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;

import org.hamcrest.Matchers;
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
        EncryptionKey seeded = EncryptionKey.generateKeyWithPassword("seed");

        EncryptionKey randomPublic = random.publicOnly();
        EncryptionKey seededPublic = seeded.publicOnly();

        byte[] randomSecret = EncryptionKey.combinedSecret(random.getPrivateKey(null), seededPublic);
        byte[] seededSecret = EncryptionKey.combinedSecret(seeded.getPrivateKey("seed"), randomPublic);
        assertThat(randomSecret, Is.is(seededSecret));
    }

    @Test
    public void testLegacy() throws JsonProcessingException {
        final String TEST_KEY = "{\"publicKey\":\"7EYB6KASD5WYZHNNXRZN4YJYOKOF46MRQFOZXIDZO4HKXZWBXAHQ\",\"salt\":\"J534MRW3TSGJ4GETNRN6ZZDNCNQCCTPTRGMOSGVN7SY2S6B2WMWQ\",\"keyData\":\"IWAK4LTIWU2ZYIGYFWQA2LRGDBKQ2QE2XFG7NB2JUZVHIRYALPBA\"}";
        final String TEST_DISPLAY_PRIVATE_KEY = "=G6CK5R7UXJAPGR7K66QMLGJHWNEW6XULCPERIQTH4MOFKDOB4GTQ";
        EncryptionKey rebuilt = ENCRYPTION_KEY_READER.readValue(TEST_KEY);

        assertThat(TEST_DISPLAY_PRIVATE_KEY,
                Is.is(rebuilt.getPrivateKey("another").getDisplayPrivateKey()));
    }

    @Test
    public void testChangedPassword() throws JsonProcessingException {
        EncryptionKey seeded1 = EncryptionKey.generateKeyWithPassword("seed");
        EncryptionKey seeded2 = EncryptionKey.changeEncryptionPassword("seed",
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
        EncryptionKey seeded1 = EncryptionKey.generateKeyWithPassword("seed");
        EncryptionKey additionalKey = EncryptionKey.generateKeys();
        seeded1.getPrivateKey("seed").getAdditionalKeyManager().addNewKey(additionalKey, Mockito.mock(ManifestManager.class));

        EncryptionKey seeded2 = EncryptionKey.changeEncryptionPassword("seed",
                "another",
                seeded1
        );

        String publicOnlyKey = ENCRYPTION_KEY_WRITER.writeValueAsString(seeded2.publicOnlyHash());
        EncryptionKey rebuilt = ENCRYPTION_KEY_READER.readValue(publicOnlyKey);

        String seeded1Private = seeded1.getPrivateKey("seed").getAdditionalKeyManager().findMatchingPrivateKey(additionalKey).getPrivateKey(null).getDisplayPrivateKey();
        String seeded2Private = seeded2.getPrivateKey("another").getAdditionalKeyManager().findMatchingPrivateKey(additionalKey).getPrivateKey(null).getDisplayPrivateKey();
        String rebuildPrivate = rebuilt.getPrivateKey("another").getAdditionalKeyManager().findMatchingPrivateKey(additionalKey).getPrivateKey(null).getDisplayPrivateKey();

        assertThat(seeded1Private, Is.is(seeded2Private));
        assertThat(seeded1Private, Is.is(rebuildPrivate));
        assertThat(seeded1.getPrivateKey("seed").getDisplayPrivateKey(), Matchers.is(seeded2.getPrivateKey("another").getDisplayPrivateKey()));
        assertThat(seeded1.getBlockHashSalt(), Matchers.is(seeded2.getBlockHashSalt()));
    }


    @Test
    public void testNewPrivateKey() throws IOException {
        EncryptionKey seeded1 = EncryptionKey.generateKeyWithPassword("seed");
        EncryptionKey additionalKey = EncryptionKey.generateKeys();
        seeded1.getPrivateKey("seed").getAdditionalKeyManager().addNewKey(additionalKey, Mockito.mock(ManifestManager.class));

        EncryptionKey seeded2 = EncryptionKey.changeEncryptionPasswordWithNewPrivateKey(
                "another",
                seeded1.getPrivateKey("seed")
        );

        String publicOnlyKey = ENCRYPTION_KEY_WRITER.writeValueAsString(seeded2.publicOnlyHash());
        EncryptionKey rebuilt = ENCRYPTION_KEY_READER.readValue(publicOnlyKey);

        String seeded1Private = seeded1.getPrivateKey("seed").getAdditionalKeyManager().findMatchingPrivateKey(additionalKey).getPrivateKey(null).getDisplayPrivateKey();
        String seeded2Private = seeded2.getPrivateKey("another").getAdditionalKeyManager().findMatchingPrivateKey(additionalKey).getPrivateKey(null).getDisplayPrivateKey();
        String rebuildPrivate = rebuilt.getPrivateKey("another").getAdditionalKeyManager().findMatchingPrivateKey(additionalKey).getPrivateKey(null).getDisplayPrivateKey();

        assertThat(seeded1Private, Is.is(seeded2Private));
        assertThat(seeded1Private, Is.is(rebuildPrivate));
        assertThat(seeded1.getPrivateKey("seed").getDisplayPrivateKey(), Matchers.not(seeded2.getPrivateKey("another").getDisplayPrivateKey()));
        assertThat(seeded1.getBlockHashSalt(), Matchers.is(seeded2.getBlockHashSalt()));
    }

    @Test
    public void testSharingKey() throws IOException {
        EncryptionKey seeded1 = EncryptionKey.generateKeyWithPassword("seed");
        EncryptionKey seeded2 = EncryptionKey.changeEncryptionPassword("seed",
                "another",
                seeded1
        );


        String publicOnlyKey = ENCRYPTION_KEY_WRITER.writeValueAsString(seeded2.publicOnly());
        EncryptionKey rebuilt = ENCRYPTION_KEY_READER.readValue(publicOnlyKey);
        assertThat(seeded1.getSharingPublicKey(), Is.is(seeded2.getSharingPublicKey()));
        assertThat(seeded1.getSharingPublicKey(), Is.is(rebuilt.getSharingPublicKey()));

        String seeded1Private = seeded1.getPrivateKey("seed").getAdditionalKeyManager().findMatchingPrivateKey(seeded1.getSharingPublicEncryptionKey()).getPrivateKey(null).getDisplayPrivateKey();
        String seeded2Private = seeded2.getPrivateKey("another").getAdditionalKeyManager().findMatchingPrivateKey(seeded2.getSharingPublicEncryptionKey()).getPrivateKey(null).getDisplayPrivateKey();
        String rebuildPrivate = rebuilt.getPrivateKey("another").getAdditionalKeyManager().findMatchingPrivateKey(rebuilt.getSharingPublicEncryptionKey()).getPrivateKey(null).getDisplayPrivateKey();

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
        EncryptionKey key1 = EncryptionKey.generateKeyWithPassword("abc");
        EncryptionKey key2 = EncryptionKey.generateKeyWithPassword("def");

        byte[] secret = EncryptionKey.combinedSecret(key1.getPrivateKey("abc"), key2);

        key1 = mapper.readValue(mapper.writeValueAsString(key1), EncryptionKey.class);
        key2 = mapper.readValue(mapper.writeValueAsString(key2), EncryptionKey.class);

        assertThat(EncryptionKey.combinedSecret(key1.getPrivateKey("abc"), key2), Is.is(secret));
        assertThat(EncryptionKey.combinedSecret(key2.getPrivateKey("def"), key1), Is.is(secret));
    }
}
