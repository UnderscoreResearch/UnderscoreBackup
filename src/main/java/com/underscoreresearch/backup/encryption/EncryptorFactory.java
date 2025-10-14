package com.underscoreresearch.backup.encryption;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import org.reflections.Reflections;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Factory for creating and managing encryptor instances.
 * This class discovers and instantiates encryptors based on their plugin annotations
 * and provides utility methods for encryption operations.
 */
public final class EncryptorFactory {
    private static final Map<String, Holder> encryptors;

    /*
     * Static block to initialize the encryptors map using reflection.
     */
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

    /**
     * Gets a list of supported encryption types.
     *
     * @return A sorted list of supported encryption type identifiers
     */
    public static List<String> supportedEncryptions() {
        List<String> ret = new ArrayList<>(encryptors.keySet());
        ret.sort(String::compareTo);
        return ret;
    }

    /**
     * Checks if an encryption type is supported.
     *
     * @param encryption The encryption type identifier
     * @return True if the encryption type is supported
     * @throws IllegalArgumentException If the encryption type is not supported
     */
    public static boolean hasEncryptor(String encryption) {
        Holder holder = encryptors.get(encryption);
        if (holder == null)
            throw new IllegalArgumentException("Unsupported encryption type \"" + encryption + "\"");
        return true;
    }

    /**
     * Checks if an encryption type requires storage metadata.
     *
     * @param encryption The encryption type identifier
     * @return True if the encryption type requires storage metadata
     */
    public static boolean requireStorage(String encryption) {
        Holder holder = encryptors.get(encryption);
        if (holder == null)
            return false;
        EncryptorPlugin plugin = holder.clz.getAnnotation(EncryptorPlugin.class);
        return plugin.requireStorage();
    }

    /**
     * Gets an encryptor instance for the specified encryption type.
     *
     * @param encryption The encryption type identifier
     * @return The encryptor instance
     * @throws IllegalArgumentException If the encryption type is not supported
     */
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

    /**
     * Injects a custom encryptor implementation.
     *
     * @param encryption The encryption type identifier
     * @param encryptor The encryptor instance
     */
    public static void injectEncryptor(String encryption, Encryptor encryptor) {
        Holder holder = new Holder(encryptor.getClass());
        holder.encryptor = encryptor;
        encryptors.put(encryption, holder);
    }

    /**
     * Encrypts a data block using the specified encryption type and keys.
     *
     * @param encryption The encryption type identifier
     * @param storage The storage metadata
     * @param data The data to encrypt
     * @param key The identity keys to use for encryption
     * @return The encrypted data
     * @throws GeneralSecurityException If encryption fails
     */
    public static byte[] encryptBlock(String encryption, BackupBlockStorage storage, byte[] data, IdentityKeys key)
            throws GeneralSecurityException {
        if (storage != null) {
            storage.setEncryption(encryption);
        }
        return getEncryptor(encryption).encryptBlock(storage, data, key);
    }

    /**
     * Decodes (decrypts) a data block using the appropriate encryptor.
     *
     * @param storage The storage metadata containing the encryption type
     * @param encryptedData The encrypted data
     * @param key The private keys to use for decryption
     * @return The decrypted data
     * @throws GeneralSecurityException If decryption fails
     */
    public static byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData, IdentityKeys.PrivateKeys key)
            throws GeneralSecurityException {
        return getEncryptor(storage.getEncryption()).decodeBlock(storage, encryptedData, key);
    }

    /**
     * Holder class for lazy initialization of encryptor instances.
     */
    private static class Holder {
        public Class<? extends Encryptor> clz;
        public Encryptor encryptor;

        public Holder(Class<? extends Encryptor> clz) {
            this.clz = clz;
        }
    }
}
