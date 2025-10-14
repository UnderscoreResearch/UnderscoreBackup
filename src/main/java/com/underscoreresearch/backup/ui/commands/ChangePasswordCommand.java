package com.underscoreresearch.backup.ui.commands;

import com.underscoreresearch.backup.ui.ConfigurationValidator;
import com.underscoreresearch.backup.ui.PasswordReader;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.RepositoryOpenMode;
import com.underscoreresearch.backup.file.implementation.BackupStatsLogger;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.io.UploadScheduler;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.manifest.implementation.AdditionalManifestManager;
import com.underscoreresearch.backup.manifest.implementation.ManifestManagerImpl;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.service.api.model.SourceResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import static com.underscoreresearch.backup.ui.commands.ConfigureCommand.reloadIfRunning;
import static com.underscoreresearch.backup.ui.commands.GenerateKeyCommand.getDefaultEncryptionFileName;
import static com.underscoreresearch.backup.configuration.CommandLineModule.CONFIG_DATA;
import static com.underscoreresearch.backup.configuration.CommandLineModule.FORCE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.INSTALLATION_IDENTITY;
import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.configuration.CommandLineModule.SOURCE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.SOURCE_CONFIG;

/**
 * Command for changing the password of an existing encryption key.
 * This command allows users to change their private key password, optionally re-encrypting
 * all backup data with the new key.
 */
@Slf4j
@CommandPlugin(value = "change-password", description = "Change the password of an existing key",
        needConfiguration = false, readonlyRepository = false)
public class ChangePasswordCommand extends Command {

    public static final String RE_KEY_OPERATION_NAME = "Re-keying log";

    /**
     * Changes the password for a private key without re-encrypting the backup data.
     *
     * @param commandLine The command line arguments
     * @param oldPassword The current password for the private key
     * @param newPassword The new password to set for the private key
     * @return The path to the saved key file
     * @throws IOException If there is an error saving the key file
     * @throws GeneralSecurityException If there is an error changing the encryption password
     */
    public static String changePrivateKeyPassword(CommandLine commandLine, String oldPassword, String newPassword) throws IOException, GeneralSecurityException {
        EncryptionIdentity oldIdentity = InstanceFactory.getInstance(EncryptionIdentity.class);

        EncryptionIdentity newIdentity = oldIdentity.changeEncryptionPassword(oldPassword, newPassword, false);

        return saveKeyFile(commandLine, newIdentity);
    }

    /**
     * Saves the encryption identity to a key file.
     *
     * @param commandLine The command line arguments
     * @param newIdentity The encryption identity to save
     * @return The path to the saved key file
     * @throws IOException If there is an error saving the key file
     */
    public static String saveKeyFile(CommandLine commandLine, EncryptionIdentity newIdentity) throws IOException {
        File keyFile = getDefaultEncryptionFileName(commandLine);

        String file = saveKeyFile(keyFile, newIdentity);
        if (InstanceFactory.hasConfiguration(false)) {
            InstanceFactory.getInstance(ManifestManager.class).updateKeyData(newIdentity);
        }

        return file;
    }

    /**
     * Saves the encryption identity to a specified key file.
     *
     * @param keyFile The file to save the key to
     * @param newIdentity The encryption identity to save
     * @return The path to the saved key file
     * @throws IOException If there is an error saving the key file
     */
    public static String saveKeyFile(File keyFile, EncryptionIdentity newIdentity) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(keyFile)) {
            newIdentity.writeKey(EncryptionIdentity.KeyFormat.PUBLIC, stream);
        }

        IOUtils.setOwnerOnlyPermissions(keyFile);

        return keyFile.getAbsolutePath();
    }

    /**
     * Generates a new private key and re-encrypts all backup data with the new key.
     *
     * @param manifestManager The manifest manager
     * @param repository The metadata repository
     * @param fileName The file to save the new key to
     * @param oldPassword The current password for the private key
     * @param newPassword The new password to set for the private key
     * @return The new encryption identity
     * @throws IOException If there is an error generating the new key
     * @throws GeneralSecurityException If there is an error with encryption operations
     */
    public static EncryptionIdentity generateNewPrivateKey(ManifestManager manifestManager, MetadataRepository repository,
                                                           File fileName, String oldPassword, String newPassword) throws IOException,
            GeneralSecurityException {
        BackupConfiguration configuration = InstanceFactory.getInstance(SOURCE_CONFIG, BackupConfiguration.class);
        ConfigurationValidator.validateConfiguration(configuration, false, false);

        EncryptionIdentity oldIdentity = InstanceFactory.getInstance(EncryptionIdentity.class);

        // Make sure we have flushed all existing log files.
        manifestManager.initialize(InstanceFactory.getInstance(LogConsumer.class), true);

        EncryptionIdentity newIdentity = oldIdentity.changeEncryptionPassword(oldPassword, newPassword, true);

        // Generate a new key in memory and then rewrite all the backup metadata with the new key.
        repository.open(RepositoryOpenMode.READ_WRITE);

        ChangePrivateKeyManifestManager changePrivateKeyManifestManager = new ChangePrivateKeyManifestManager(
                configuration,
                InstanceFactory.getInstance(MANIFEST_LOCATION),
                InstanceFactory.getInstance(RateLimitController.class),
                InstanceFactory.getInstance(ServiceManager.class),
                InstanceFactory.getInstance(INSTALLATION_IDENTITY),
                repository,
                fileName,
                newIdentity,
                oldIdentity.getPrivateKeys(oldPassword),
                null,
                InstanceFactory.getInstance(AdditionalManifestManager.class),
                InstanceFactory.getInstance(UploadScheduler.class));

        manifestManager.setDependentManager(changePrivateKeyManifestManager);

        try {

            if (!changePrivateKeyManifestManager.optimizeLog(repository, InstanceFactory.getInstance(LogConsumer.class), false)) {
                throw new IOException("Failed to rewrite all the log files with the new key");
            }
            return newIdentity;
        } catch (Exception exc) {
            log.error("Error changing private key", exc);
            log.error("You must rerun the password change to complete the change or the backup can be corrupted");
        } finally {
            changePrivateKeyManifestManager.shutdown();
            manifestManager.setDependentManager(null);
        }
        return null;
    }

    /**
     * Removes the secret key recovery from the service.
     *
     * @param serviceManager The service manager
     */
    public static void removeSecret(ServiceManager serviceManager) {
        if (serviceManager.getSourceId() != null) {
            try {
                SourceResponse source = serviceManager.call(null, (api) -> api.getSource(serviceManager.getSourceId()));
                if (source.getSecretRegion() != null) {
                    serviceManager.call(source.getSecretRegion(),
                            (api) -> api.deleteSecret(serviceManager.getSourceId()));
                    log.warn("Removed private key recovery from region \"{}\"", source.getSecretRegion());
                }
            } catch (IOException e) {
                log.error("Failed to remove old private key recovery", e);
            }
        }
    }

    /**
     * Executes the change-password command.
     *
     * @param commandLine The command line arguments
     * @throws Exception If an error occurs during command execution
     */
    public void executeCommand(CommandLine commandLine) throws Exception {
        if (commandLine.getArgList().size() != 1) {
            throw new ParseException("Too many arguments for command");
        }

        if (commandLine.hasOption(SOURCE)) {
            throw new ParseException("Can not change password with source specified");
        }

        String firstTry = PasswordReader.readPassword("Please enter the new password for the private key: ");
        if (firstTry == null) {
            System.exit(1);
        }
        String secondTry
                = PasswordReader.readPassword("Reenter the new password for the private key: ");
        if (secondTry == null) {
            System.exit(1);
        }
        if (!firstTry.equals(secondTry)) {
            System.out.println("Passwords do not match");
            System.exit(1);
        }

        if (commandLine.hasOption(FORCE)) {
            MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
            ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);

            try {
                repository.open(RepositoryOpenMode.READ_ONLY);
                if (repository.isErrorsDetected()) {
                    log.error("Detected corruption in local metadata repository need to repair before changing private key");
                    return;
                }

                File fileName = getDefaultEncryptionFileName(InstanceFactory.getInstance(CommandLine.class));

                generateNewPrivateKey(manifestManager, repository, fileName, getPassword(), firstTry);

                System.out.println("Wrote public key to " + fileName);

                removeSecret(InstanceFactory.getInstance(ServiceManager.class));
            } finally {
                manifestManager.shutdown();
                repository.close();
            }
        } else {
            String file = changePrivateKeyPassword(commandLine, getPassword(), firstTry);

            System.out.println("Wrote public key to " + file);
        }

        reloadIfRunning();
    }

    /**
     * Private inner class that handles re-keying operations for the backup data.
     */
    private static class ChangePrivateKeyManifestManager extends ManifestManagerImpl {
        private final File keyFile;
        private final MetadataRepository repository;
        private final IdentityKeys.PrivateKeys oldPrivateKey;

        /**
         * Constructor for the ChangePrivateKeyManifestManager.
         *
         * @param configuration The backup configuration
         * @param manifestLocation The manifest location
         * @param rateLimitController The rate limit controller
         * @param serviceManager The service manager
         * @param installationIdentity The installation identity
         * @param repository The metadata repository
         * @param keyFile The key file to save
         * @param publicKey The public key
         * @param oldPrivateKey The old private key
         * @param statsLogger The stats logger
         * @param additionalManifestManager The additional manifest manager
         * @param uploadScheduler The upload scheduler
         */
        public ChangePrivateKeyManifestManager(BackupConfiguration configuration,
                                               String manifestLocation,
                                               RateLimitController rateLimitController,
                                               ServiceManager serviceManager,
                                               String installationIdentity,
                                               MetadataRepository repository,
                                               File keyFile,
                                               EncryptionIdentity publicKey,
                                               IdentityKeys.PrivateKeys oldPrivateKey,
                                               BackupStatsLogger statsLogger,
                                               AdditionalManifestManager additionalManifestManager,
                                               UploadScheduler uploadScheduler) {
            super(configuration, manifestLocation, rateLimitController, serviceManager,
                    installationIdentity, null, false, false, publicKey, publicKey.getPrimaryKeys(), statsLogger,
                    additionalManifestManager, uploadScheduler);

            this.keyFile = keyFile;
            this.repository = repository;
            initialize((LogConsumer) repository, true);

            this.oldPrivateKey = oldPrivateKey;
        }

        /**
         * Overrides the uploadPending method to prevent premature writing of config files.
         *
         * @param logConsumer The log consumer
         */
        @Override
        protected void uploadPending(LogConsumer logConsumer) {
            // This is a NOP and we already do this first with the regular manifest manager and we don't want to
            // prematurely write config files with the new key.
        }

        /**
         * Starts the optimize operation for re-keying.
         */
        protected void startOptimizeOperation() {
            startOperation(RE_KEY_OPERATION_NAME);
        }

        /**
         * Shuts down the manifest manager after waiting for completion.
         *
         * @throws IOException If there is an error during shutdown
         */
        @Override
        public void shutdown() throws IOException {
            waitCompletedOperation();
            super.shutdown();
        }

        /**
         * Optimizes a block by re-encrypting it with the new key.
         *
         * @param block The block to optimize
         * @return The optimized block
         * @throws IOException If there is an error optimizing the block
         */
        @Override
        protected BackupBlock optimizeBlock(BackupBlock block) throws IOException {
            List<BackupBlockStorage> newStorage;
            if (block.getStorage() != null) {
                newStorage = new ArrayList<>();
                for (BackupBlockStorage storage : block.getStorage()) {
                    try {
                        newStorage.add(getEncryptor().reKeyStorage(storage.toBuilder().build(),
                                oldPrivateKey,
                                getEncryptionIdentity().getPrimaryKeys()));
                    } catch (GeneralSecurityException e) {
                        throw new IOException(e);
                    }
                }
            } else {
                newStorage = null;
            }

            BackupBlock newBlock = block.toBuilder().
                    storage(newStorage).
                    build();

            repository.addTemporaryBlock(newBlock);

            return newBlock;
        }

        /**
         * Deletes log files after saving the new key and configuration.
         *
         * @param lastOldLogFile The last old log file
         * @throws IOException If there is an error deleting log files
         */
        @Override
        public void deleteLogFiles(String lastOldLogFile) throws IOException {
            // The new key and configuration is written once all the new logs are written with the new key, but
            // before we delete all the old files. In case of a failure you will still have all the logs which
            // means you can recover from the failure even though you might get errors on the results.
            saveKeyFile(keyFile, getEncryptionIdentity());
            IOUtils.setOwnerOnlyPermissions(keyFile);
            repository.installTemporaryBlocks();

            uploadConfigData(CONFIGURATION_FILENAME,
                    InstanceFactory.getInstance(CONFIG_DATA).getBytes(StandardCharsets.UTF_8),
                    true, null);
            uploadPublicKey(getEncryptionIdentity());

            completeUploads();

            super.deleteLogFiles(lastOldLogFile);
        }

        /**
         * Waits for eventual consistency if the IO provider doesn't have consistent writes.
         */
        @Override
        protected void awaitEventualConsistency() {
            try {
                if (!((IOIndex) getIoProvider()).hasConsistentWrites()) {
                    log.info("Waiting 20 seconds for eventual consistency");
                    Thread.sleep(EVENTUAL_CONSISTENCY_TIMEOUT_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
