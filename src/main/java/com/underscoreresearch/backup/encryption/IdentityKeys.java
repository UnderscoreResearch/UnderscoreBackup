package com.underscoreresearch.backup.encryption;

import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptorFormat.PUBLIC_KEY;
import static com.underscoreresearch.backup.encryption.encryptors.BaseAesEncryptor.applyKeyData;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;
import static java.util.stream.Collectors.toMap;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.encryption.encryptors.kyber.KyberKeyMethod;
import com.underscoreresearch.backup.encryption.encryptors.x25519.X25519;
import com.underscoreresearch.backup.encryption.encryptors.x25519.X25519KeyMethod;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IdentityKeys {
    public final static String X25519_KEY = PUBLIC_KEY;
    public final static String KYBER_KEY = "y";
    public static final int SYMMETRIC_KEY_SIZE = 32;
    private final static X25519KeyMethod X25519_KEY_METHOD = new X25519KeyMethod();
    private final static KyberKeyMethod KYBER_KEY_METHOD = new KyberKeyMethod();
    private final static ObjectReader READER = MAPPER.readerFor(IdentityKeys.class);
    private final static ObjectWriter WRITER = MAPPER.writerFor(IdentityKeys.class);
    private final static ObjectWriter PRIVATE_WRITER = MAPPER.writerFor(IdentityKeys.PrivateIdentityKeys.class);
    private final static ObjectReader LEGACY_READER = MAPPER.readerFor(LegacyEncryptionKey.class);
    @JsonProperty("k")
    private Map<String, PublicKey> keys;

    @JsonCreator
    public IdentityKeys(@JsonProperty("k") Map<String, PublicKey> keys) {
        this.keys = keys;
    }

    public static IdentityKeys createIdentityKeys(EncryptionIdentity.PrivateIdentity privateIdentity)
            throws GeneralSecurityException {
        return new IdentityKeys(Map.of(
                X25519_KEY, X25519_KEY_METHOD.createKeyPair(privateIdentity),
                KYBER_KEY, KYBER_KEY_METHOD.createKeyPair(privateIdentity)
        ));
    }

    public static IdentityKeys createIdentityKeys(EncryptionIdentity.PrivateIdentity privateIdentity, byte[] privateKey)
            throws GeneralSecurityException {
        return new IdentityKeys(Map.of(
                X25519_KEY, new PublicKey(X25519.publicFromPrivate(privateKey), privateKey, privateIdentity)));
    }

    public static IdentityKeys fromString(String str, EncryptionIdentity.PrivateIdentity privateIdentity)
            throws GeneralSecurityException {
        if (str.startsWith("{")) {
            try {
                IdentityKeys ret = READER.readValue(str);
                if (privateIdentity != null) {
                    ret.unpackKeys(privateIdentity);
                }
                return ret;
            } catch (Exception e) {
                try {
                    LegacyEncryptionKey key = LEGACY_READER.readValue(str);
                    if (key.getPrivateKeySerializing() != null) {
                        byte[] privateKey = key.getPrivateKey(null).getPrivateKey();
                        byte[] publicKey = X25519.publicFromPrivate(privateKey);
                        return new IdentityKeys(Map.of(
                                X25519_KEY, new PublicKey(publicKey, privateKey, privateIdentity)
                        ));
                    }
                } catch (JsonProcessingException ignored) {
                }
                throw new GeneralSecurityException(e);
            }
        } else if (str.startsWith("=")) {
            byte[] privateKey = Hash.decodeBytes(str.substring(1));
            byte[] publicKey = X25519.publicFromPrivate(privateKey);

            return new IdentityKeys(Map.of(
                    X25519_KEY, new PublicKey(publicKey, privateKey, privateIdentity)
            ));
        } else {
            byte[] publicKey = Hash.decodeBytes(str);
            return new IdentityKeys(Map.of(
                    X25519_KEY, new PublicKey(publicKey)
            ));
        }
    }

    public EncryptionParameters getEncryptionParameters(Set<String> keysToUse) throws GeneralSecurityException {
        byte[] secret = null;
        Map<String, PublicKeyMethod.EncapsulatedKey> encapsulatedKeys = new HashMap<>();
        for (String keyType : keysToUse) {
            PublicKeyMethod method = switch (keyType) {
                case X25519_KEY -> X25519_KEY_METHOD;
                case KYBER_KEY -> KYBER_KEY_METHOD;
                default -> throw new InvalidKeyException("Unknown key type: " + keyType);
            };
            PublicKey publicKey = keys.get(keyType);
            if (publicKey == null) {
                throw new InvalidKeyException("No key of type: " + keyType);
            }
            PublicKeyMethod.GeneratedKey generatedKey = method.generateNewSecret(publicKey);
            if (secret != null) {
                secret = applyKeyData(secret, generatedKey.getSecret());
            } else {
                secret = generatedKey.getSecret();
            }
            encapsulatedKeys.put(keyType, generatedKey);
        }
        return new EncryptionParameters(secret, encapsulatedKeys);
    }

    @JsonIgnore
    public String getKeyIdentifier() {
        return Hash.encodeBytes(keys.get(X25519_KEY).getPublicKey());
    }

    @JsonIgnore
    public String getPublicKeyHash() {
        return keys.get(X25519_KEY).getPublicKeyHash();
    }

    @Override
    public String toString() {
        try {
            return WRITER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public IdentityKeys withoutPublicKeys() {
        return new IdentityKeys(keys.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(),
                        entry.getValue().withoutPublicKey()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    public void unpackKeys(EncryptionIdentity.PrivateIdentity privateIdentity) throws GeneralSecurityException {
        for (PublicKey key : keys.values()) {
            key.getPrivateKey(privateIdentity);
        }
        if (keys.get(KYBER_KEY) == null) {
            keys = Map.of(X25519_KEY, keys.get(X25519_KEY),
                    KYBER_KEY, KYBER_KEY_METHOD.createKeyPair(privateIdentity));
        }
    }

    public PrivateKeys getPrivateKeys(EncryptionIdentity.PrivateIdentity privateIdentity) {
        return new PrivateKeys(privateIdentity);
    }

    public IdentityKeys changeEncryption(EncryptionIdentity.PrivateIdentity existingPI,
                                         EncryptionIdentity.PrivateIdentity newPI)
            throws GeneralSecurityException {
        Map<String, PublicKey> map = new HashMap<>();
        for (Map.Entry<String, PublicKey> stringPublicKeyEntry : keys.entrySet()) {
            PublicKey.PrivateKey pk = stringPublicKeyEntry.getValue().getPrivateKey(existingPI);
            map.put(stringPublicKeyEntry.getKey(),
                    new PublicKey(pk.getPublicKey().getPublicKey(), pk.getPrivateKey(), newPI));
        }
        return new IdentityKeys(map);
    }

    @JsonIgnore
    public boolean hasPrivateKey() {
        return keys.values().stream().anyMatch(t -> t.getEncryptedPrivateKey() != null);
    }

    @JsonIgnore
    public boolean needKeyUnpack() {
        return keys.values().stream().anyMatch(t -> t.getPublicKey() == null);
    }

    @JsonIgnore
    public String getPrivateKeyString(EncryptionIdentity.PrivateIdentity privateIdentity) throws GeneralSecurityException {
        try {
            return PRIVATE_WRITER.writeValueAsString(new PrivateIdentityKeys(keys, privateIdentity));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @JsonIgnore
    public String getPublicKeyString() {
        try {
            TreeMap<String, PublicKey> sortedKeys = new TreeMap<>();
            keys.forEach((key, value) -> sortedKeys.put(key, new PublicKey(value.getPublicKey())));
            return WRITER.writeValueAsString(new IdentityKeys(sortedKeys));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public EncryptionIdentity toPublicEncryptionIdentity() {
        EncryptionIdentity ret = new EncryptionIdentity();
        ret.primaryKeys = new IdentityKeys(keys.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(),
                        new PublicKey(entry.getValue().getPublicKey())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        return ret;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdentityKeys that = (IdentityKeys) o;
        PublicKey k1 = keys.get(X25519_KEY);
        PublicKey k2 = that.keys.get(X25519_KEY);
        return Objects.equals(k1.getPublicKeyHash(), k2.getPublicKeyHash());
    }

    @Override
    public int hashCode() {
        PublicKey k1 = keys.get(X25519_KEY);
        return Objects.hash(k1.getPublicKeyString(), k1.getEncryptedPublicKeyString());
    }

    // This is a bit tricky, but basically the private keys serialize into something that can be expanded to public keys
    // with properly encrypted private keys. This should only be needed for non service managed sharing.
    private static class PrivateIdentityKeys {
        @JsonProperty("k")
        Map<String, PublicKey.PrivateKey> keys;

        public PrivateIdentityKeys(Map<String, PublicKey> keys, EncryptionIdentity.PrivateIdentity privateIdentity)
                throws GeneralSecurityException {
            Map<String, PublicKey.PrivateKey> map = new TreeMap<>();
            for (Map.Entry<String, PublicKey> entry : keys.entrySet()) {
                map.put(entry.getKey(), entry.getValue().getPrivateKey(privateIdentity));
            }
            this.keys = map;
        }
    }

    @AllArgsConstructor
    @Getter
    public static class EncryptionParameters {
        private final byte[] secret;
        private final Map<String, PublicKeyMethod.EncapsulatedKey> keys;
    }

    @Getter
    @RequiredArgsConstructor
    public class PrivateKeys {
        private final EncryptionIdentity.PrivateIdentity privateIdentity;

        public IdentityKeys getIdentity() {
            return IdentityKeys.this;
        }

        public PublicKey.PrivateKey getPrivateKey(String type) throws GeneralSecurityException {
            PublicKey key = IdentityKeys.this.keys.get(type);
            return key.getPrivateKey(privateIdentity);
        }

        public byte[] recreateSecret(Map<String, PublicKeyMethod.EncapsulatedKey> encapsulatedKeys)
                throws GeneralSecurityException {
            byte[] combinedSecret = null;
            for (Map.Entry<String, PublicKeyMethod.EncapsulatedKey> entry : encapsulatedKeys.entrySet()) {
                PublicKeyMethod method = switch (entry.getKey()) {
                    case X25519_KEY -> X25519_KEY_METHOD;
                    case KYBER_KEY -> KYBER_KEY_METHOD;
                    default -> throw new InvalidKeyException("Unknown key type: " + entry.getKey());
                };
                PublicKey publicKey = keys.get(entry.getKey());
                if (publicKey == null) {
                    throw new InvalidKeyException("No key of type: " + entry.getKey());
                }
                PublicKey.PrivateKey privateKey = publicKey.getPrivateKey(privateIdentity);
                byte[] secret = method.recreateSecret(privateKey, entry.getValue());
                if (combinedSecret != null) {
                    combinedSecret = applyKeyData(combinedSecret, secret);
                } else {
                    combinedSecret = secret;
                }
            }
            return combinedSecret;
        }
    }
}
