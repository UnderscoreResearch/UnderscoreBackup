package com.underscoreresearch.backup.encryption.encryptors;

import static com.underscoreresearch.backup.encryption.encryptors.NoneEncryptor.NONE_ENCRYPTION;

import java.security.GeneralSecurityException;

import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.encryption.EncryptorPlugin;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.model.BackupBlockStorage;

@EncryptorPlugin(value = NONE_ENCRYPTION)
public class NoneEncryptor implements Encryptor {
    public static final String NONE_ENCRYPTION = "NONE";

    @Override
    public byte[] encryptBlock(BackupBlockStorage storage, byte[] data, IdentityKeys keys) throws GeneralSecurityException {
        return data;
    }

    @Override
    public byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData, IdentityKeys.PrivateKeys keys) throws GeneralSecurityException {
        return encryptedData;
    }

    @Override
    public BackupBlockStorage reKeyStorage(BackupBlockStorage storage, IdentityKeys.PrivateKeys oldPrivateKey, IdentityKeys newPublicKey) throws GeneralSecurityException {
        return storage;
    }
}
