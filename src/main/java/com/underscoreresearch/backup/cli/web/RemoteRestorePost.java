package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.commands.GenerateKeyCommand.getDefaultEncryptionFileName;
import static com.underscoreresearch.backup.cli.web.ConfigurationPost.updateConfiguration;
import static com.underscoreresearch.backup.configuration.EncryptionModule.ROOT_KEY;
import static com.underscoreresearch.backup.io.IOUtils.deleteFile;
import static com.underscoreresearch.backup.manifest.implementation.BaseManifestManagerImpl.PUBLICKEY_FILENAME;
import static com.underscoreresearch.backup.utils.SerializationUtils.ENCRYPTION_KEY_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.ENCRYPTION_KEY_WRITER;

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
public class RemoteRestorePost extends BaseWrap {

    public RemoteRestorePost() {
        super(new Implementation());
    }

    public static byte[] downloadKeyData(String password, String source) throws ParseException, IOException {
        IOProvider provider = getIoProvider(source);
        byte[] keyData = provider.download(PUBLICKEY_FILENAME);
        EncryptionKey encryptionKey = ENCRYPTION_KEY_READER.readValue(keyData);

        if (!Strings.isNullOrEmpty(source) && encryptionKey.getSalt() == null) {
            encryptionKey = InstanceFactory.getInstance(ROOT_KEY, EncryptionKey.class)
                    .getPrivateKey(password)
                    .getAdditionalKeyManager().findMatchingPrivateKey(encryptionKey);
            if (encryptionKey == null) {
                throw new ParseException("No private key found");
            }
        } else {
            try {
                encryptionKey.getPrivateKey(password);
            } catch (RuntimeException exc) {
                throw new ParseException("Invalid password provided for restore");
            }
        }
        return ENCRYPTION_KEY_WRITER.writeValueAsBytes(encryptionKey.publicOnly());
    }

    public static IOProvider getIoProvider(String source) {
        BackupDestination destination = getManifestDestination(source);
        return IOProviderFactory.getProvider(destination);
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

    private static class Implementation extends ExclusiveImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            String password = PrivateKeyRequest.decodePrivateKeyRequest(req);
            try {
                try {
                    byte[] keyData;
                    try {
                        keyData = downloadKeyData(password, null);
                    } catch (ParseException exc) {
                        return messageJson(403, exc.getMessage());
                    }

                    File privateKeyFile = getDefaultEncryptionFileName(InstanceFactory
                            .getInstance(CommandLine.class));
                    try (FileOutputStream writer = new FileOutputStream(privateKeyFile)) {
                        writer.write(keyData);
                    }

                    InstanceFactory.reloadConfiguration(null);
                    try {
                        String config = RebuildRepositoryCommand.downloadRemoteConfiguration(null, password);

                        updateConfiguration(config, true, true, true);
                    } catch (Exception exc) {
                        log.error("Failed to download configuration", exc);
                        deleteFile(privateKeyFile);
                        return messageJson(500, "Failed to download configuration");
                    }

                    // We want the rebild to start before we return.
                    InstanceFactory.reloadConfiguration(
                            () -> RebuildRepositoryCommand.rebuildFromLog(password, true));
                    return messageJson(200, "Remote restore initiated");
                } catch (Exception exc) {
                    return BaseWrap.messageJson(400, "Couldn't fetch remote configuration");
                }
            } catch (Exception exc) {
                log.error("Failed to read existing config", exc);
            }
            return messageJson(404, "No valid config available");
        }

        @Override
        protected String getBusyMessage() {
            return "Restoring from remote server";
        }
    }
}
