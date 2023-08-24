package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.cli.commands.ConfigureCommand.reloadIfRunning;
import static com.underscoreresearch.backup.cli.commands.GenerateKeyCommand.getDefaultEncryptionFileName;
import static com.underscoreresearch.backup.configuration.CommandLineModule.CONFIG_DATA;
import static com.underscoreresearch.backup.configuration.CommandLineModule.FORCE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.INSTALLATION_IDENTITY;
import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.configuration.CommandLineModule.SOURCE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.SOURCE_CONFIG;
import static com.underscoreresearch.backup.utils.SerializationUtils.ENCRYPTION_KEY_WRITER;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.cli.ConfigurationValidator;
import com.underscoreresearch.backup.cli.PasswordReader;
import com.underscoreresearch.backup.cli.web.ConfigurationPost;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.implementation.BackupStatsLogger;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.io.UploadScheduler;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.manifest.implementation.AdditionalManifestManager;
import com.underscoreresearch.backup.manifest.implementation.OptimizingManifestManager;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;

@Slf4j
@CommandPlugin(value = "change-password", description = "Change the password of an existing key",
        needConfiguration = false, readonlyRepository = false)
public class ChangePasswordCommand extends Command {
    public static String changePrivateKeyPassword(CommandLine commandLine, String oldPassword, String newPassword) throws IOException {
        EncryptionKey key = InstanceFactory.getInstance(EncryptionKey.class);

        EncryptionKey encryptionKey = EncryptionKey.changeEncryptionPassword(oldPassword, newPassword, key);

        File keyFile = getDefaultEncryptionFileName(commandLine);

        ENCRYPTION_KEY_WRITER.writeValue(keyFile,
                encryptionKey.publicOnly());

        ConfigurationPost.setOwnerOnlyPermissions(keyFile);

        if (InstanceFactory.hasConfiguration(false)) {
            InstanceFactory.getInstance(ManifestManager.class).updateKeyData(encryptionKey);
        }

        return keyFile.getAbsolutePath();
    }

    private static void generateNewPrivateKey(CommandLine commandLine, String oldPassword, String newPassword) throws IOException, ParseException {
        BackupConfiguration configuration = InstanceFactory.getInstance(SOURCE_CONFIG, BackupConfiguration.class);
        ConfigurationValidator.validateConfiguration(configuration, false, false);
        BackupDestination destination = configuration.getDestinations().get(configuration.getManifest()
                .getDestination());

        EncryptionKey oldPublicKey = InstanceFactory.getInstance(EncryptionKey.class);
        EncryptionKey.PrivateKey oldPrivateKey = oldPublicKey.getPrivateKey(oldPassword);

        // Make sure we have flushed all existing log files.
        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
        manifestManager.initialize(InstanceFactory.getInstance(LogConsumer.class), true);

        EncryptionKey newKey = EncryptionKey.changeEncryptionPasswordWithNewPrivateKey(newPassword, oldPrivateKey);

        // Generate a new key in memory and then rewrite all the backup metadata with the new key.
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
        repository.open(false);

        try {
            File fileName = getDefaultEncryptionFileName(commandLine);
            ChangePrivateKeyManifestManager changePrivateKeyManifestManager = new ChangePrivateKeyManifestManager(
                    configuration,
                    InstanceFactory.getInstance(MANIFEST_LOCATION),
                    InstanceFactory.getInstance(RateLimitController.class),
                    InstanceFactory.getInstance(ServiceManager.class),
                    InstanceFactory.getInstance(INSTALLATION_IDENTITY),
                    repository,
                    fileName,
                    newKey,
                    oldPrivateKey,
                    null,
                    InstanceFactory.getInstance(AdditionalManifestManager.class),
                    InstanceFactory.getInstance(UploadScheduler.class));

            changePrivateKeyManifestManager.optimizeLog(repository, InstanceFactory.getInstance(LogConsumer.class));
            changePrivateKeyManifestManager.shutdown();

            System.out.println("Wrote public key to " + fileName);
        } catch (Exception exc) {
            log.error("Error changing private key", exc);
            log.error("You must rerun the password change command with the --force option to complete the change or the backup can be corrupted");
        }

        manifestManager.shutdown();
        repository.close();
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
            generateNewPrivateKey(commandLine, getPassword(), firstTry);
        } else {
            String file = changePrivateKeyPassword(commandLine, getPassword(), firstTry);

            System.out.println("Wrote public key to " + file);
        }

        reloadIfRunning();
    }

    private static class ChangePrivateKeyManifestManager extends OptimizingManifestManager {
        private final File keyFile;
        private final MetadataRepository repository;
        private final EncryptionKey.PrivateKey oldPrivateKey;

        public ChangePrivateKeyManifestManager(BackupConfiguration configuration,
                                               String manifestLocation,
                                               RateLimitController rateLimitController,
                                               ServiceManager serviceManager,
                                               String installationIdentity,
                                               MetadataRepository repository,
                                               File keyFile,
                                               EncryptionKey publicKey,
                                               EncryptionKey.PrivateKey oldPrivateKey,
                                               BackupStatsLogger statsLogger,
                                               AdditionalManifestManager additionalManifestManager,
                                               UploadScheduler uploadScheduler) throws IOException {
            super(configuration, manifestLocation, rateLimitController, serviceManager,
                    installationIdentity, null, false, publicKey, statsLogger,
                    additionalManifestManager, uploadScheduler);

            this.keyFile = keyFile;
            this.repository = repository;
            initialize((LogConsumer) repository, true);

            this.oldPrivateKey = oldPrivateKey;
        }

        @Override
        protected void uploadPending(LogConsumer logConsumer) throws IOException {
            // This is a NOP and we already do this first with the regular manifest manager and we don't want to
            // prematurely write config files with the new key.
        }

        @Override
        protected BackupBlock optimizeBlock(BackupBlock block) throws IOException {
            List<BackupBlockStorage> newStorage;
            if (block.getStorage() != null) {
                newStorage = block.getStorage().stream()
                        .map(storage -> getEncryptor().reKeyStorage(storage.toBuilder().build(),
                                oldPrivateKey, getPublicKey())).toList();
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
        public void deleteLogFiles(List<String> existingLogs) throws IOException {
            // The new key and configuration is written once all the new logs are written with the new key, but
            // before we delete all the old files. In case of a failure you will still have all the logs which
            // means you can recover from the failure even though you might get errors on the results.
            ENCRYPTION_KEY_WRITER.writeValue(keyFile,
                    getPublicKey().publicOnly());
            ConfigurationPost.setOwnerOnlyPermissions(keyFile);
            repository.installTemporaryBlocks();

            uploadConfigData(CONFIGURATION_FILENAME,
                    InstanceFactory.getInstance(CONFIG_DATA).getBytes(StandardCharsets.UTF_8),
                    true, null);
            uploadPublicKey(getPublicKey());

            completeUploads();

            super.deleteLogFiles(existingLogs);
        }
    }
}
