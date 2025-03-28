package com.underscoreresearch.backup.cli.commands;

import com.google.common.base.Strings;
import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.cli.PasswordReader;
import com.underscoreresearch.backup.cli.web.ConfigurationPost;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.EncryptionModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.service.api.model.SourceResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;

import static com.underscoreresearch.backup.cli.commands.ConfigureCommand.reloadIfRunning;
import static com.underscoreresearch.backup.cli.commands.RebuildRepositoryCommand.downloadRemoteConfiguration;
import static com.underscoreresearch.backup.cli.web.RemoteRestorePost.downloadKeyData;
import static com.underscoreresearch.backup.cli.web.SourceSelectPost.downloadSourceConfig;
import static com.underscoreresearch.backup.cli.web.SourceSelectPost.validatePrivateKey;
import static com.underscoreresearch.backup.io.IOUtils.createDirectory;

@CommandPlugin(value = "download-config", description = "Download config from manifest destination",
        readonlyRepository = false, supportSource = true, needConfiguration = false, needPrivateKey = false)
@Slf4j
public class DownloadConfigCommand extends Command {

    public static void storeKeyData(String key, String source) throws ParseException, IOException {
        byte[] keyData;
        try {
            keyData = downloadKeyData(key, source);
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
        File keyFile = new File(CommandLineModule.getKeyFileName(source));
        createDirectory(keyFile.getParentFile(), true);
        try (FileOutputStream outputStream = new FileOutputStream(keyFile)) {
            outputStream.write(keyData);
        }
        IOUtils.setOwnerOnlyPermissions(keyFile);
    }

    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        try {
            String source = InstanceFactory.getAdditionalSource();
            SourceResponse sourceResponse = InstanceFactory.getInstance(SourceResponse.class);

            String key = EncryptionModule.getPassword();
            if (Strings.isNullOrEmpty(key))
                key = PasswordReader.readPassword("Enter password for private key: ");
            if (key == null) {
                System.exit(1);
            }

            // This can be null if no source is specified even though the model says it can't.
            if (sourceResponse.getSourceId() == null) {
                storeKeyData(key, source);

                InstanceFactory.reloadConfigurationWithSource();
                String config = downloadRemoteConfiguration(source, key);
                if (source == null) {
                    ConfigurationPost.updateConfiguration(config, false, false, false);

                    log.info("Successfully downloaded and replaced the configuration file");
                } else {
                    ConfigurationPost.updateSourceConfiguration(config, false);
                    log.info("Successfully downloaded and the configuration file for \"{}\"", source);
                }
            } else {
                EncryptionIdentity.PrivateIdentity privateKey = validatePrivateKey(sourceResponse, key);
                if (privateKey == null) {
                    throw new ParseException("Invalid password provided for restore");
                }

                String config = downloadSourceConfig(source, sourceResponse, privateKey);
                if (config == null) {
                    throw new ParseException("Failed to download remote configuration");
                }

                ConfigurationPost.updateSourceConfiguration(config, false);
                log.info("Successfully downloaded and the configuration file for \"{}\"", sourceResponse.getName());
            }

            reloadIfRunning();
        } catch (Exception exc) {
            log.error("Failed to download and replace config", exc);
        }
    }
}
