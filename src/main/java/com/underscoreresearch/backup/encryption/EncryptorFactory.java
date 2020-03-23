package com.underscoreresearch.backup.encryption;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import org.reflections.Reflections;

import java.util.*;

public final class EncryptorFactory {
    private static Map<String, Class> encryptors;

    static {
        encryptors = new HashMap<>();

        Reflections reflections = InstanceFactory.getReflections();
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(EncryptorPlugin.class);

        for (Class<?> clz : classes) {
            EncryptorPlugin plugin = clz.getAnnotation(EncryptorPlugin.class);
            encryptors.put(plugin.value(), clz);
        }
    }

    public static List<String> supportedEncryptions() {
        List<String> ret = new ArrayList<>(encryptors.keySet());
        ret.sort(String::compareTo);
        return ret;
    }

    public static Encryptor getEncryptor(String encryption) {
        Class clz = encryptors.get(encryption);
        if (clz == null)
            throw new IllegalArgumentException("Unsupported encryption type " + encryption);
        return (Encryptor) InstanceFactory.getInstance(clz);
    }

    public static byte[] encryptBlock(String encryption, BackupBlockStorage storage, byte[] data) {
        storage.setEncryption(encryption);
        return getEncryptor(encryption).encryptBlock(data);
    }

    public static byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData) {
        return getEncryptor(storage.getEncryption()).decodeBlock(encryptedData);
    }
}
