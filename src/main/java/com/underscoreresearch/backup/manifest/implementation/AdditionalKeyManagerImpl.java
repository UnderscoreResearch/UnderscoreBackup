package com.underscoreresearch.backup.manifest.implementation;

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
import com.underscoreresearch.backup.encryption.AesEncryptor;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.manifest.AdditionalKeyManager;
import com.underscoreresearch.backup.manifest.ManifestManager;

@Slf4j
public class AdditionalKeyManagerImpl implements AdditionalKeyManager {
    private final static ObjectReader READER = MAPPER.readerFor(new TypeReference<List<String>>() {
    });
    private final static ObjectWriter WRITER = MAPPER.writerFor(new TypeReference<List<String>>() {
    });
    private static AesEncryptor ENCRYPTOR = new AesEncryptor();
    private final List<EncryptionKey> keys;
    private final EncryptionKey.PrivateKey privateKey;

    public AdditionalKeyManagerImpl(EncryptionKey.PrivateKey privateKey) throws IOException {
        this.privateKey = privateKey;
        keys = new ArrayList<>();

        if (privateKey.getParent().getEncryptedAdditionalKeys() != null) {
            List<String> privateKeys = READER.readValue(ENCRYPTOR.decodeBlock(null,
                    Hash.decodeBytes(privateKey.getParent().getEncryptedAdditionalKeys()), privateKey));
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

    private void updateKeys(ManifestManager manifestManager) throws IOException {
        privateKey.getParent().setEncryptedAdditionalKeys(Hash.encodeBytes(ENCRYPTOR.encryptBlock(null,
                WRITER.writeValueAsBytes(keys.stream().map(key -> key.getPrivateKey(null)
                        .getDisplayPrivateKey()).collect(Collectors.toList())),
                privateKey.getParent())));

        manifestManager.updateKeyData(privateKey.getParent());
    }

    @Override
    public EncryptionKey findMatchingPrivateKey(EncryptionKey publicKey) {
        for (EncryptionKey key : keys) {
            if (key.getPublicKey().equals(publicKey.getPublicKey())) {
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
