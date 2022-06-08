package com.underscoreresearch.backup.encryption;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.underscoreresearch.backup.encryption.x25519.X25519;

@Slf4j
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
public class PublicKeyEncrypion {
    private static final int FIXED_ITERATIONS = 64 * 1024;
    private byte[] publicKey;
    private byte[] salt;
    private byte[] privateKey;
    private byte[] passphraseKey;

    @JsonProperty
    public String getPublicKey() {
        if (publicKey != null)
            return Hash.encodeBytes(publicKey);
        return null;
    }

    @JsonProperty
    public String getSalt() {
        if (salt != null)
            return Hash.encodeBytes(salt);
        return null;
    }

    @JsonProperty
    public String getPrivateKey() {
        if (privateKey != null)
            return Hash.encodeBytes(privateKey);
        return null;
    }

    @JsonProperty
    public String getPassphraseKey() {
        if (passphraseKey != null)
            return Hash.encodeBytes(passphraseKey);
        return null;
    }

    @JsonProperty
    public void setPublicKey(String publicKey) {
        if (publicKey != null)
            this.publicKey = Hash.decodeBytes(publicKey);
        else
            this.publicKey = null;
    }

    @JsonProperty
    public void setPassphraseKey(String passphraseKey) {
        if (passphraseKey != null)
            this.passphraseKey = Hash.decodeBytes(passphraseKey);
        else
            this.passphraseKey = null;
    }

    @JsonProperty
    public void setPrivateKey(String privateKey) {
        if (privateKey != null)
            this.privateKey = Hash.decodeBytes(privateKey);
        else
            this.privateKey = null;
    }

    @JsonProperty
    public void setSalt(String salt) {
        if (salt != null)
            this.salt = Hash.decodeBytes(salt);
        else
            this.salt = null;
    }

    public static byte[] combinedSecret(PublicKeyEncrypion privateKey, PublicKeyEncrypion publicKey) {
        try {
            return X25519.computeSharedSecret(privateKey.privateKey, publicKey.publicKey);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    public static PublicKeyEncrypion generateKeys() {
        PublicKeyEncrypion ret = new PublicKeyEncrypion();
        ret.privateKey = X25519.generatePrivateKey();
        try {
            ret.publicKey = X25519.publicFromPrivate(ret.privateKey);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
        return ret;
    }

    public static PublicKeyEncrypion changeEncryptionPassphrase(String passphrase, PublicKeyEncrypion key) {
        if (key.privateKey == null) {
            throw new IllegalArgumentException("Key missing private key for changing passphrase");
        }

        PublicKeyEncrypion ret = generateKeyWithPassphrase(passphrase, null);
        AesEncryptor aesEncryptor = new AesEncryptor(ret);
        byte[] privateKey = aesEncryptor.encryptBlock(null, key.privateKey);
        ret.passphraseKey = privateKey;
        ret.publicKey = key.publicKey;
        ret.privateKey = key.privateKey;
        return ret;
    }

    public static PublicKeyEncrypion generateKeyWithPassphrase(String passphrase, PublicKeyEncrypion key) {
        try {
            byte[] saltData;
            if (key == null || key.getSalt() == null) {
                SecureRandom random = new SecureRandom();
                saltData = new byte[32];
                random.nextBytes(saltData);
            } else {
                saltData = Hash.decodeBytes(key.getSalt());
            }
            PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), saltData, FIXED_ITERATIONS, 32 * 8);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            byte[] bytes = skf.generateSecret(spec).getEncoded();

            bytes[0] = (byte) (bytes[0] | 7);
            bytes[31] = (byte) (bytes[31] & 63);
            bytes[31] = (byte) (bytes[31] | 128);

            PublicKeyEncrypion ret = new PublicKeyEncrypion();

            if (key != null) {
                if (key.passphraseKey != null) {
                    PublicKeyEncrypion unpackKey = new PublicKeyEncrypion();
                    unpackKey.privateKey = bytes;
                    AesEncryptor aesEncryptor = new AesEncryptor(unpackKey);
                    bytes = aesEncryptor.decodeBlock(null, key.passphraseKey);
                }
                ret.passphraseKey = key.passphraseKey;
            }

            ret.privateKey = bytes;
            ret.publicKey = X25519.publicFromPrivate(ret.privateKey);
            ret.salt = saltData;
            return ret;
        } catch (InvalidKeyException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    public PublicKeyEncrypion publicOnly() {
        PublicKeyEncrypion ret = new PublicKeyEncrypion();
        ret.publicKey = publicKey;
        ret.salt = salt;
        ret.passphraseKey = passphraseKey;
        return ret;
    }
}
