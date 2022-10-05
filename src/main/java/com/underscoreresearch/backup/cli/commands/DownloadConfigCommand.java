package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.cli.commands.RebuildRepositoryCommand.downloadRemoteConfiguration;
import static com.underscoreresearch.backup.cli.web.ConfigurationPost.setReadOnlyFilePermissions;
import static com.underscoreresearch.backup.cli.web.RemoteRestorePost.downloadKeyData;
import static com.underscoreresearch.backup.configuration.CommandLineModule.KEY_FILE_NAME;
import static com.underscoreresearch.backup.configuration.CommandLineModule.PRIVATE_KEY_SEED;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import com.google.common.base.Strings;
import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.cli.PassphraseReader;
import com.underscoreresearch.backup.cli.web.ConfigurationPost;
import com.underscoreresearch.backup.configuration.InstanceFactory;

@CommandPlugin(value = "download-config", description = "Download config from manifest destination",
        readonlyRepository = false, supportSource = true, needConfiguration = false, needPrivateKey = false)
@Slf4j
public class DownloadConfigCommand extends Command {

    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        try {
            String source = InstanceFactory.getAdditionalSource();
            String key = InstanceFactory.getInstance(PRIVATE_KEY_SEED);
            if (Strings.isNullOrEmpty(key))
                key = PassphraseReader.readPassphrase("Enter passphrase for private key: ");
            if (key == null) {
                System.exit(1);
            }
            storeKeyData(key, source);

            InstanceFactory.reloadConfiguration(key, source);
            String config = downloadRemoteConfiguration(source);
            if (source == null) {
                ConfigurationPost.updateConfiguration(config, false, false);
                log.info("Successfully downloaded and replaced the configuration file");
            } else {
                ConfigurationPost.updateSourceConfiguration(config, false);
                log.info("Successfully downloaded and the configuration file for {}", source);
            }
        } catch (Exception exc) {
            log.error("Failed to download and replace config", exc);
        }
    }

    public static void storeKeyData(String key, String source) throws ParseException, IOException {
        byte[] keyData = downloadKeyData(key, source);
        File keyFile = new File(InstanceFactory.getInstance(KEY_FILE_NAME));
        keyFile.getParentFile().mkdirs();
        try (FileOutputStream outputStream = new FileOutputStream(keyFile)) {
            outputStream.write(keyData);
        }
        setReadOnlyFilePermissions(keyFile);
    }
}
