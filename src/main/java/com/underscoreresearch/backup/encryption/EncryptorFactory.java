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
    private static final Map<String, Holder> encryptors;

    static {
        encryptors = new HashMap<>();

        Reflections reflections = InstanceFactory.getReflections();
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(EncryptorPlugin.class);

        for (Class<?> untyped : classes) {
            @SuppressWarnings("unchecked")
            Class<? extends Encryptor> clz = (Class<Encryptor>) untyped;
            EncryptorPlugin plugin = clz.getAnnotation(EncryptorPlugin.class);
            encryptors.put(plugin.value(), new Holder(clz));
        }
    }

    public static List<String> supportedEncryptions() {
        List<String> ret = new ArrayList<>(encryptors.keySet());
        ret.sort(String::compareTo);
        return ret;
    }

    public static boolean hasEncryptor(String encryption) {
        Holder holder = encryptors.get(encryption);
        if (holder == null)
            throw new IllegalArgumentException("Unsupported encryption type \"" + encryption + "\"");
        return true;
    }

    public static boolean requireStorage(String encryption) {
        Holder holder = encryptors.get(encryption);
        if (holder == null)
            return false;
        EncryptorPlugin plugin = holder.clz.getAnnotation(EncryptorPlugin.class);
        return plugin.requireStorage();
    }

    public static Encryptor getEncryptor(String encryption) {
        Holder holder = encryptors.get(encryption);
        if (holder == null)
            throw new IllegalArgumentException("Unsupported encryption type \"" + encryption + "\"");
        if (holder.encryptor == null) {
            synchronized (holder) {
                if (holder.encryptor == null) {
                    holder.encryptor = InstanceFactory.getInstance(holder.clz);
                }
            }
        }
        return holder.encryptor;
    }

    public static void injectEncryptor(String encryption, Encryptor encryptor) {
        Holder holder = new Holder(encryptor.getClass());
        holder.encryptor = encryptor;
        encryptors.put(encryption, holder);
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

    private static class Holder {
        public Class<? extends Encryptor> clz;
        public Encryptor encryptor;

        public Holder(Class<? extends Encryptor> clz) {
            this.clz = clz;
        }
    }
}
