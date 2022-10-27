package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.commands.GenerateKeyCommand.getDefaultEncryptionFileName;
import static com.underscoreresearch.backup.cli.web.ConfigurationPost.updateConfiguration;
import static com.underscoreresearch.backup.configuration.EncryptionModule.ROOT_KEY;
import static com.underscoreresearch.backup.utils.SerializationUtils.ENCRYPTION_KEY_READER;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.takes.Request;
import org.takes.Response;

import com.google.common.base.Strings;
import com.underscoreresearch.backup.cli.commands.RebuildRepositoryCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;

@Slf4j
public class RemoteRestorePost extends JsonWrap {

    public RemoteRestorePost() {
        super(new Implementation());
    }

    public static byte[] downloadKeyData(String passphrase, String source) throws ParseException, IOException {
        IOProvider provider = getIoProvider(source);
        byte[] keyData = provider.download("/publickey.json");
        EncryptionKey encryptionKey = ENCRYPTION_KEY_READER.readValue(keyData);

        if (!Strings.isNullOrEmpty(source) && encryptionKey.getSalt() == null) {
            encryptionKey = InstanceFactory.getInstance(ROOT_KEY, EncryptionKey.class)
                    .getPrivateKey(passphrase)
                    .getAdditionalKeyManager().findMatchingPrivateKey(encryptionKey);
            if (encryptionKey == null) {
                throw new ParseException("No private key found");
            }
        } else {
            try {
                encryptionKey.getPrivateKey(passphrase);
            } catch (RuntimeException exc) {
                throw new ParseException("Invalid passphrase provided for restore");
            }
        }
        return keyData;
    }

    public static IOProvider getIoProvider(String source) {
        BackupDestination destination = getManifestDestination(source);
        IOProvider provider = IOProviderFactory.getProvider(destination);
        return provider;
    }

    public static BackupDestination getManifestDestination(String source) {
        BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);
        BackupDestination destination;
        if (Strings.isNullOrEmpty(source)) {
            destination = configuration.getDestinations().get(configuration.getManifest().getDestination());
        } else {
            destination = configuration.getAdditionalSources().get(source);
        }
        if (destination == null) {
            log.error("Manifest destination did not exist");
        }
        return destination;
    }

    private static class Implementation extends BaseImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            String passphrase = PrivateKeyRequest.decodePrivateKeyRequest(req);
            try {
                try {
                    byte[] keyData;
                    try {
                        keyData = downloadKeyData(passphrase, null);
                    } catch (ParseException exc) {
                        return messageJson(403, exc.getMessage());
                    }

                    File privateKeyFile = getDefaultEncryptionFileName(InstanceFactory
                            .getInstance(CommandLine.class));
                    try (FileOutputStream writer = new FileOutputStream(privateKeyFile)) {
                        writer.write(keyData);
                    }

                    InstanceFactory.reloadConfiguration(null, null);
                    try {
                        String config = RebuildRepositoryCommand.downloadRemoteConfiguration(null, passphrase);

                        updateConfiguration(config, true, true);
                    } catch (Exception exc) {
                        log.error("Failed to download configuration", exc);
                        privateKeyFile.delete();
                        return messageJson(500, "Failed to download configuration");
                    }

                    // We want the rebild to start before we return.
                    InstanceFactory.reloadConfiguration(null,
                            () -> RebuildRepositoryCommand.rebuildFromLog(passphrase, true));
                    return messageJson(200, "Remote restore initiated");
                } catch (Exception exc) {
                    return JsonWrap.messageJson(400, "Couldn't fetch remote configuration");
                }
            } catch (Exception exc) {
                log.error("Failed to read existing config", exc);
            }
            return messageJson(404, "No valid config available");
        }
    }
}
