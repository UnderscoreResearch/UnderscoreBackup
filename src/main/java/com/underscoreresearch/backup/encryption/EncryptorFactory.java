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
            return false;
        return true;
    }

    public static Encryptor getEncryptor(String encryption) {
        Class<? extends Encryptor> clz = encryptors.get(encryption);
        if (clz == null)
            throw new IllegalArgumentException("Unsupported encryption type " + encryption);
        return InstanceFactory.getInstance(clz);
    }

    public static byte[] encryptBlock(String encryption, BackupBlockStorage storage, byte[] data) {
        storage.setEncryption(encryption);
        return getEncryptor(encryption).encryptBlock(storage, data);
    }

    public static byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData) {
        return getEncryptor(storage.getEncryption()).decodeBlock(storage, encryptedData);
    }
}
