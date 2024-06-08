package com.underscoreresearch.backup.encryption.encryptors.kyber;

import static com.underscoreresearch.backup.encryption.IdentityKeys.SYMMETRIC_KEY_SIZE;

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

import org.bouncycastle.jcajce.SecretKeyWithEncapsulation;
import org.bouncycastle.jcajce.spec.KEMExtractSpec;
import org.bouncycastle.jcajce.spec.KEMGenerateSpec;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.spec.KyberParameterSpec;

import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.PublicKey;
import com.underscoreresearch.backup.encryption.PublicKeyMethod;
import com.underscoreresearch.backup.encryption.encryptors.BaseAesEncryptor;

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

    private static KeyGenerator createKeyPairGenerator() throws GeneralSecurityException {
        return KeyGenerator.getInstance("KYBER", "BCPQC");
    }

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

    @Override
    public GeneratedKey generateNewSecret(PublicKey publicKey) throws GeneralSecurityException {
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(publicKey.getPublicKey());
        java.security.PublicKey kyberPublicKey = KEY_FACTORY.generatePublic(x509EncodedKeySpec);
        KeyGenerator keyGen = createKeyPairGenerator();
        keyGen.init(new KEMGenerateSpec(kyberPublicKey, "AES"), EncryptionIdentity.RANDOM);
        SecretKeyWithEncapsulation secEnc = (SecretKeyWithEncapsulation) keyGen.generateKey();
        return new GeneratedKey(secEnc.getEncoded(), secEnc.getEncapsulation());
    }

    @Override
    public GeneratedKey encapsulateSecret(PublicKey publicKey, byte[] secret) throws GeneralSecurityException {
        return encapsulateSecret(this, publicKey, secret);
    }

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
