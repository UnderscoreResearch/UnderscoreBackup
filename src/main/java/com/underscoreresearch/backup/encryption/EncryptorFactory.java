package com.underscoreresearch.backup.encryption;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.reflections.Reflections;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupBlockStorage;

public final class EncryptorFactory {
    private static Map<String, Class<? extends Encryptor>> encryptors;

    static {
        encryptors = new HashMap<>();

        Reflections reflections = InstanceFactory.getReflections();
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(EncryptorPlugin.class);

        for (Class<?> untyped : classes) {
            @SuppressWarnings("unchecked")
            Class<? extends Encryptor> clz = (Class<Encryptor>) untyped;
            EncryptorPlugin plugin = clz.getAnnotation(EncryptorPlugin.class);
            encryptors.put(plugin.value(), clz);
        }
    }

    public static List<String> supportedEncryptions() {
        List<String> ret = new ArrayList<>(encryptors.keySet());
        ret.sort(String::compareTo);
        return ret;
    }

    public static boolean hasEncryptor(String encryption) {
        Class<? extends Encryptor> clz = encryptors.get(encryption);
        if (clz == null)
            throw new IllegalArgumentException("Unsupported encryption type " + encryption);
        return true;
    }

    public static boolean requireStorage(String encryption) {
        Class<? extends Encryptor> clz = encryptors.get(encryption);
        if (clz == null)
            return false;
        EncryptorPlugin plugin = clz.getAnnotation(EncryptorPlugin.class);
        return plugin.requireStorage();
    }

    public static Encryptor getEncryptor(String encryption) {
        Class<? extends Encryptor> clz = encryptors.get(encryption);
        if (clz == null)
            throw new IllegalArgumentException("Unsupported encryption type " + encryption);
        return InstanceFactory.getInstance(clz);
    }

    public static byte[] encryptBlock(String encryption, BackupBlockStorage storage, byte[] data, EncryptionKey key) {
        if (storage != null) {
            storage.setEncryption(encryption);
        }
        return getEncryptor(encryption).encryptBlock(storage, data, key);
    }

    public static byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData, EncryptionKey.PrivateKey key) {
        return getEncryptor(storage.getEncryption()).decodeBlock(storage, encryptedData, key);
    }
}
