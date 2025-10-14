package com.underscoreresearch.backup.encryption.encryptors.kyber;

import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.PublicKey;
import com.underscoreresearch.backup.encryption.PublicKeyMethod;
import com.underscoreresearch.backup.encryption.encryptors.BaseAesEncryptor;
import org.bouncycastle.jcajce.SecretKeyWithEncapsulation;
import org.bouncycastle.jcajce.spec.KEMExtractSpec;
import org.bouncycastle.jcajce.spec.KEMGenerateSpec;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.spec.KyberParameterSpec;

import javax.crypto.KeyGenerator;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import static com.underscoreresearch.backup.encryption.IdentityKeys.SYMMETRIC_KEY_SIZE;

/**
 * Implementation of the PublicKeyMethod interface using the Kyber post-quantum key encapsulation mechanism.
 * This class provides methods for creating key pairs, generating and encapsulating secrets,
 * and recreating secrets from encapsulated data using the Kyber algorithm.
 */
public class KyberKeyMethod implements PublicKeyMethod {
    private static final int KEY_SIZE = 1568;
    private static final KeyPairGenerator KYBER_KEY_GENERATOR;
    private static final KeyFactory KEY_FACTORY;

    static {
        if (Security.getProvider("BCPQC") == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }

        try {
            KYBER_KEY_GENERATOR = KeyPairGenerator.getInstance("KYBER", "BCPQC");
            KYBER_KEY_GENERATOR.initialize(KyberParameterSpec.kyber1024, EncryptionIdentity.RANDOM);
            KEY_FACTORY = KeyFactory.getInstance("KYBER", "BCPQC");
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a KeyGenerator for Kyber operations.
     *
     * @return The KeyGenerator instance
     * @throws GeneralSecurityException If the generator cannot be created
     */
    private static KeyGenerator createKeyPairGenerator() throws GeneralSecurityException {
        return KeyGenerator.getInstance("KYBER", "BCPQC");
    }

    /**
     * Encapsulates an existing secret for a public key.
     *
     * @param keyMethod The key method to use for generating a new secret
     * @param publicKey The public key to use for encapsulation
     * @param secret The secret to encapsulate
     * @return The encapsulated secret
     * @throws GeneralSecurityException If encapsulation fails
     * @throws IllegalArgumentException If the secret length is invalid
     */
    public static GeneratedKey encapsulateSecret(PublicKeyMethod keyMethod, PublicKey publicKey, byte[] secret) throws GeneralSecurityException {
        if (secret.length != SYMMETRIC_KEY_SIZE) {
            throw new IllegalArgumentException("Invalid secret length");
        }

        GeneratedKey key = keyMethod.generateNewSecret(publicKey);

        byte[] data = new byte[secret.length + key.getEncapsulation().length];
        byte[] keyData = BaseAesEncryptor.applyKeyData(key.getSecret(), secret);
        System.arraycopy(keyData, 0, data, 0, keyData.length);
        System.arraycopy(key.getEncapsulation(), 0, data, keyData.length, key.getEncapsulation().length);

        return new GeneratedKey(secret, data);
    }

    /**
     * Creates a new Kyber key pair.
     *
     * @param privateIdentity The private identity to use for key encryption
     * @return The created public key with encrypted private key
     * @throws GeneralSecurityException If key generation fails
     */
    @Override
    public PublicKey createKeyPair(EncryptionIdentity.PrivateIdentity privateIdentity)
            throws GeneralSecurityException {
        synchronized (KYBER_KEY_GENERATOR) {
            KeyPair kp = KYBER_KEY_GENERATOR.generateKeyPair();
            byte[] pk = kp.getPrivate().getEncoded();
            byte[] pub = kp.getPublic().getEncoded();
            return new PublicKey(pub, pk, privateIdentity);
        }
    }

    /**
     * Generates a new secret and encapsulates it for the given public key.
     *
     * @param publicKey The public key to use for encapsulation
     * @return The generated secret and its encapsulation
     * @throws GeneralSecurityException If secret generation fails
     */
    @Override
    public GeneratedKey generateNewSecret(PublicKey publicKey) throws GeneralSecurityException {
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(publicKey.getPublicKey());
        java.security.PublicKey kyberPublicKey = KEY_FACTORY.generatePublic(x509EncodedKeySpec);
        KeyGenerator keyGen = createKeyPairGenerator();
        keyGen.init(new KEMGenerateSpec(kyberPublicKey, "AES"), EncryptionIdentity.RANDOM);
        SecretKeyWithEncapsulation secEnc = (SecretKeyWithEncapsulation) keyGen.generateKey();
        return new GeneratedKey(secEnc.getEncoded(), secEnc.getEncapsulation());
    }

    /**
     * Encapsulates an existing secret for the given public key.
     *
     * @param publicKey The public key to use for encapsulation
     * @param secret The secret to encapsulate
     * @return The encapsulated secret
     * @throws GeneralSecurityException If encapsulation fails
     */
    @Override
    public GeneratedKey encapsulateSecret(PublicKey publicKey, byte[] secret) throws GeneralSecurityException {
        return encapsulateSecret(this, publicKey, secret);
    }

    /**
     * Recreates a secret from an encapsulated key using a private key.
     *
     * @param privateKey The private key to use for decapsulation
     * @param generatedKey The encapsulated key
     * @return The recreated secret
     * @throws GeneralSecurityException If secret recreation fails
     * @throws IllegalArgumentException If the encapsulation length is invalid
     */
    @Override
    public byte[] recreateSecret(PublicKey.PrivateKey privateKey, EncapsulatedKey generatedKey) throws GeneralSecurityException {
        KeyGenerator keyGen = createKeyPairGenerator();
        PrivateKey kyberPrivateKey = KEY_FACTORY.generatePrivate(new PKCS8EncodedKeySpec(privateKey.getPrivateKey()));
        if (generatedKey.getEncapsulation().length == KEY_SIZE) {
            keyGen.init(new KEMExtractSpec(kyberPrivateKey, generatedKey.getEncapsulation(), "AES"), new SecureRandom());
            SecretKeyWithEncapsulation secEnc = (SecretKeyWithEncapsulation) keyGen.generateKey();
            return secEnc.getEncoded();
        }
        if (generatedKey.getEncapsulation().length == KEY_SIZE + SYMMETRIC_KEY_SIZE) {
            byte[] keyData = new byte[SYMMETRIC_KEY_SIZE];
            byte[] publicKey = new byte[KEY_SIZE];
            System.arraycopy(generatedKey.getEncapsulation(), 0, keyData, 0, keyData.length);
            System.arraycopy(generatedKey.getEncapsulation(), keyData.length, publicKey, 0, publicKey.length);
            keyGen.init(new KEMExtractSpec(kyberPrivateKey, publicKey, "AES"), new SecureRandom());
            SecretKeyWithEncapsulation secEnc = (SecretKeyWithEncapsulation) keyGen.generateKey();
            return BaseAesEncryptor.applyKeyData(secEnc.getEncoded(), keyData);
        }
        throw new IllegalArgumentException("Invalid encapsulation length");
    }
}
