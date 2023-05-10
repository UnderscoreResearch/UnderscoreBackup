package com.underscoreresearch.backup.encryption;

import static com.underscoreresearch.backup.encryption.EncryptionKey.ENCRYPTOR;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.manifest.AdditionalKeyManager;
import com.underscoreresearch.backup.manifest.ManifestManager;

@Slf4j
public class AdditionalKeyManagerImpl implements AdditionalKeyManager {
    private final static ObjectReader READER = MAPPER.readerFor(new TypeReference<List<String>>() {
    });
    private final static ObjectWriter WRITER = MAPPER.writerFor(new TypeReference<List<String>>() {
    });
    private final List<EncryptionKey> keys;
    private final EncryptionKey.PrivateKey privateKey;

    public AdditionalKeyManagerImpl(EncryptionKey.PrivateKey privateKey) throws IOException {
        this.privateKey = privateKey;
        keys = new ArrayList<>();

        if (privateKey.getParent().getEncryptedAdditionalKeys() != null) {
            List<String> privateKeys;
            try {
                privateKeys = READER.readValue(ENCRYPTOR.decodeBlock(null,
                        Hash.decodeBytes64(privateKey.getParent().getEncryptedAdditionalKeys()), privateKey));
            } catch (Exception exc) {
                // This is only for backwards compatability.
                privateKeys = READER.readValue(ENCRYPTOR.decodeBlock(null,
                        Hash.decodeBytes(privateKey.getParent().getEncryptedAdditionalKeys()), privateKey));
            }
            for (String key : privateKeys) {
                try {
                    keys.add(EncryptionKey.createWithPrivateKey(key));
                } catch (InvalidKeyException e) {
                    throw new IOException("Invalid key", e);
                }
            }
        }
    }

    @Override
    public EncryptionKey generateNewKey(ManifestManager manifestManager) throws IOException {
        EncryptionKey ret = EncryptionKey.generateKeys();

        keys.add(ret);

        updateKeys(manifestManager);

        return ret;
    }

    @Override
    public synchronized boolean addNewKey(EncryptionKey newKey, ManifestManager manifestManager) throws IOException {
        if (keys.stream().anyMatch(t -> t.getPublicKey().equals(newKey.getPublicKey()))) {
            log.warn("The provided key already exists in the keyring");
            return false;
        }
        keys.add(newKey);

        updateKeys(manifestManager);
        return true;
    }

    public void writeAdditionalKeys(EncryptionKey.PrivateKey otherKey) throws IOException {
        otherKey.getParent().setEncryptedAdditionalKeys(Hash.encodeBytes64(ENCRYPTOR.encryptBlock(null,
                WRITER.writeValueAsBytes(keys.stream().map(key -> key.getPrivateKey(null)
                        .getDisplayPrivateKey()).collect(Collectors.toList())),
                otherKey.getParent())));
    }

    private void updateKeys(ManifestManager manifestManager) throws IOException {
        writeAdditionalKeys(privateKey);

        if (manifestManager != null) {
            manifestManager.updateKeyData(privateKey.getParent());
        }
    }

    @Override
    public EncryptionKey findMatchingPrivateKey(EncryptionKey publicKey) {
        for (EncryptionKey key : keys) {
            if (key.getPublicKeyHash().equals(publicKey.getPublicKeyHash())) {
                return key;
            }
        }
        return null;
    }

    @Override
    public synchronized EncryptionKey[] getKeys() {
        EncryptionKey[] ret = new EncryptionKey[keys.size()];
        return keys.toArray(ret);
    }
}
