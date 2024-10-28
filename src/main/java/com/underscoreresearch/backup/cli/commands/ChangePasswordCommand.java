package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.cli.commands.ConfigureCommand.reloadIfRunning;
import static com.underscoreresearch.backup.cli.commands.GenerateKeyCommand.getDefaultEncryptionFileName;
import static com.underscoreresearch.backup.configuration.CommandLineModule.CONFIG_DATA;
import static com.underscoreresearch.backup.configuration.CommandLineModule.FORCE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.INSTALLATION_IDENTITY;
import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.configuration.CommandLineModule.SOURCE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.SOURCE_CONFIG;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import com.underscoreresearch.backup.io.IOIndex;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.cli.ConfigurationValidator;
import com.underscoreresearch.backup.cli.PasswordReader;
import com.underscoreresearch.backup.cli.web.ConfigurationPost;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.RepositoryOpenMode;
import com.underscoreresearch.backup.file.implementation.BackupStatsLogger;
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

@Slf4j
@CommandPlugin(value = "change-password", description = "Change the password of an existing key",
        needConfiguration = false, readonlyRepository = false)
public class ChangePasswordCommand extends Command {

    public static final String RE_KEY_OPERATION_NAME = "Re-keying log";

    public static String changePrivateKeyPassword(CommandLine commandLine, String oldPassword, String newPassword) throws IOException, GeneralSecurityException {
        EncryptionIdentity oldIdentity = InstanceFactory.getInstance(EncryptionIdentity.class);

        EncryptionIdentity newIdentity = oldIdentity.changeEncryptionPassword(oldPassword, newPassword, false);

        return saveKeyFile(commandLine, newIdentity);
    }

    public static String saveKeyFile(CommandLine commandLine, EncryptionIdentity newIdentity) throws IOException {
        File keyFile = getDefaultEncryptionFileName(commandLine);

        String file = saveKeyFile(keyFile, newIdentity);
        if (InstanceFactory.hasConfiguration(false)) {
            InstanceFactory.getInstance(ManifestManager.class).updateKeyData(newIdentity);
        }

        return file;
    }

    public static String saveKeyFile(File keyFile, EncryptionIdentity newIdentity) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(keyFile)) {
            newIdentity.writeKey(EncryptionIdentity.KeyFormat.PUBLIC, stream);
        }

        ConfigurationPost.setOwnerOnlyPermissions(keyFile);

        return keyFile.getAbsolutePath();
    }

    public static void generateNewPrivateKey(ManifestManager manifestManager, MetadataRepository repository,
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
        } catch (Exception exc) {
            log.error("Error changing private key", exc);
            log.error("You must rerun the password change to complete the change or the backup can be corrupted");
        } finally {
            changePrivateKeyManifestManager.shutdown();
            manifestManager.setDependentManager(null);
        }
    }

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

    private static class ChangePrivateKeyManifestManager extends ManifestManagerImpl {
        private final File keyFile;
        private final MetadataRepository repository;
        private final IdentityKeys.PrivateKeys oldPrivateKey;

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
                    installationIdentity, null, false, publicKey, publicKey.getPrimaryKeys(), statsLogger,
                    additionalManifestManager, uploadScheduler);

            this.keyFile = keyFile;
            this.repository = repository;
            initialize((LogConsumer) repository, true);

            this.oldPrivateKey = oldPrivateKey;
        }

        @Override
        protected void uploadPending(LogConsumer logConsumer) {
            // This is a NOP and we already do this first with the regular manifest manager and we don't want to
            // prematurely write config files with the new key.
        }

        protected void startOptimizeOperation() {
            startOperation(RE_KEY_OPERATION_NAME);
        }

        @Override
        public void shutdown() throws IOException {
            waitCompletedOperation();
            super.shutdown();
        }

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

        @Override
        public void deleteLogFiles(String lastOldLogFile) throws IOException {
            // The new key and configuration is written once all the new logs are written with the new key, but
            // before we delete all the old files. In case of a failure you will still have all the logs which
            // means you can recover from the failure even though you might get errors on the results.
            saveKeyFile(keyFile, getEncryptionIdentity());
            ConfigurationPost.setOwnerOnlyPermissions(keyFile);
            repository.installTemporaryBlocks();

            uploadConfigData(CONFIGURATION_FILENAME,
                    InstanceFactory.getInstance(CONFIG_DATA).getBytes(StandardCharsets.UTF_8),
                    true, null);
            uploadPublicKey(getEncryptionIdentity());

            completeUploads();

            super.deleteLogFiles(lastOldLogFile);
        }

        @Override
        protected void awaitEventualConsistency() {
            try {
                if (!((IOIndex)getProvider()).hasConsistentWrites()) {
                    log.info("Waiting 20 seconds for eventual consistency");
                    Thread.sleep(20000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
