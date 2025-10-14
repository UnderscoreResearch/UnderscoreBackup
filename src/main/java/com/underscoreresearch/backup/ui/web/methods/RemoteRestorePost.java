package com.underscoreresearch.backup.ui.web.methods;

import com.google.common.base.Strings;
import com.underscoreresearch.backup.ui.commands.RebuildRepositoryCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.IOProviderUtil;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import com.underscoreresearch.backup.ui.web.PrivateKeyRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.takes.Request;
import org.takes.Response;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import static com.underscoreresearch.backup.ui.commands.GenerateKeyCommand.getDefaultEncryptionFileName;
import static com.underscoreresearch.backup.ui.web.methods.ConfigurationPost.updateConfiguration;
import static com.underscoreresearch.backup.configuration.EncryptionModule.ROOT_KEY;
import static com.underscoreresearch.backup.io.IOUtils.deleteFile;
import static com.underscoreresearch.backup.manifest.implementation.BaseManifestManagerImpl.PUBLICKEY_FILENAME;

/**
 * Web endpoint for restoring from a remote backup repository.
 * This class handles requests to restore a backup from a remote destination.
 */
@Slf4j
public class RemoteRestorePost extends BaseWrap {

    /**
     * Creates a new RemoteRestorePost instance.
     */
    public RemoteRestorePost() {
        super(new Implementation());
    }

    /**
     * Downloads key data from a backup source.
     * Validates the password and returns the key data.
     *
     * @param password The password for the encryption key
     * @param source The source to download from, or null for the default source
     * @return The key data
     * @throws ParseException If there's an error parsing the key data
     * @throws IOException If there's an error downloading the key data
     * @throws GeneralSecurityException If there's an error with the encryption
     */
    public static byte[] downloadKeyData(String password, String source) throws ParseException, IOException,
            GeneralSecurityException {
        IOProvider provider = getIoProvider(source);
        byte[] keyData = IOProviderUtil.download(provider, PUBLICKEY_FILENAME);
        EncryptionIdentity encryptionKey = EncryptionIdentity.restoreFromString(new String(keyData, StandardCharsets.UTF_8));

        if (!Strings.isNullOrEmpty(source) && encryptionKey.getSalt() == null) {
            try {
                InstanceFactory.getInstance(ROOT_KEY, EncryptionIdentity.class)
                        .getIdentityKeysForPublicIdentity(encryptionKey.getPrimaryKeys());
            } catch (IndexOutOfBoundsException exc) {
                throw new ParseException("No private key found");
            }
        } else {
            try {
                encryptionKey.unpackKeys(encryptionKey.getPrivateIdentity(password));
            } catch (RuntimeException exc) {
                throw new ParseException("Invalid password provided for restore");
            }
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            encryptionKey.writeKey(EncryptionIdentity.KeyFormat.PUBLIC, output);
            return output.toByteArray();
        }
    }

    /**
     * Gets an IO provider for a backup source.
     *
     * @param source The source to get the provider for, or null for the default source
     * @return The IO provider
     */
    public static IOProvider getIoProvider(String source) {
        BackupDestination destination = getManifestDestination(source);
        return IOProviderFactory.getProvider(destination);
    }

    /**
     * Gets the manifest destination for a backup source.
     *
     * @param source The source to get the destination for, or null for the default source
     * @return The backup destination
     */
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

    /**
     * Implementation class that handles remote restore requests.
     */
    private static class Implementation extends ExclusiveImplementation {
        /**
         * Processes a request to restore from a remote backup.
         * Downloads the key data and configuration, and initiates the rebuild process.
         *
         * @param req The HTTP request
         * @return The HTTP response
         * @throws Exception If an error occurs during processing
         */
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

        /**
         * Gets the message to display when the system is busy.
         *
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Restoring from remote server";
        }
    }
}
