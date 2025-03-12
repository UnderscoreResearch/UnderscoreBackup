package com.underscoreresearch.backup.encryption.encryptors;

import com.google.inject.Inject;
import com.underscoreresearch.backup.encryption.EncryptorPlugin;
import lombok.extern.slf4j.Slf4j;

import static com.underscoreresearch.backup.encryption.encryptors.X25519Encryptor.AES_ENCRYPTION;

/**
 * X25519 encryptor. Called AES for historical reasons (AES is used for the symmetrical cypher)
 * <p>
 * So this format is a bit of a mess in that I started using CBC encoding and padding and then realized that I really
 * should be using GCM encoding. Unfortunately I left no field for future expansion in the original format but I have
 * figured out a way to be backwards compatible and add future extensibility in case I want to change this again
 * in the future. So here is how the payload works.
 * <p>
 * First byte is a padding version indicator which can currently be 0 for CBC, 1 for GCM and 2 for GCM with a single
 * additional byte for padding its payload to an even length. This byte is missing for all legacy data created before
 * the introduction of the GCM encoding. However, any encrypted block with an even number of bytes in length will
 * assumed to be of CBC encoding. This is also why the GCM encoding needs format bytes since it can be of uneven size.
 * <p>
 * The next 12 bytes for GCM and 16 bytes for CBC contain the IV vector for the crypto.
 * <p>
 * The next 32 bytes contain the public key used to combine with the private key to create the key used for the AES256
 * algorithm.
 * <p>
 * The entire rest of the data is the encryption payload.
 * <p>
 * There is also another format used when storage is specified by default. In this format only the first byte is used
 * to specify the format and the entire rest of the payload is the encryption. The IV in this case is a 0 array, the
 * encryption key is the SHA3-256 of the payload (Which is different from the SHA-256 used to create the block ID. In
 * this format a block with the same contents will always be encrypted to exactly the same encryption payload allowing
 * for good deduplication of the data without jeopardizing the contents.
 */
@EncryptorPlugin(AES_ENCRYPTION)
@Slf4j
public class X25519Encryptor extends BaseAesEncryptor {
    public static final String AES_ENCRYPTION = "AES256";

    @Inject
    public X25519Encryptor() {
    }
}
