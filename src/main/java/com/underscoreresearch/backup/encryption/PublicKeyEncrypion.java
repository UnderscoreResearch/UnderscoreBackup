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
    public void setPublicKey(String publicKey) {
        if (publicKey != null)
            this.publicKey = Hash.decodeBytes(publicKey);
        else
            this.publicKey = null;
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

    public static PublicKeyEncrypion generateKeyWithSeed(String seed, String salt) {
        try {
            byte[] saltData;
            if (salt == null) {
                SecureRandom random = new SecureRandom();
                saltData = new byte[32];
                random.nextBytes(saltData);
            } else {
                saltData = Hash.decodeBytes(salt);
            }
            PBEKeySpec spec = new PBEKeySpec(seed.toCharArray(), saltData, FIXED_ITERATIONS, 32 * 8);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            byte[] bytes = skf.generateSecret(spec).getEncoded();

            bytes[0] = (byte) (bytes[0] | 7);
            bytes[31] = (byte) (bytes[31] & 63);
            bytes[31] = (byte) (bytes[31] | 128);
            PublicKeyEncrypion ret = new PublicKeyEncrypion();
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
        return ret;
    }
}
