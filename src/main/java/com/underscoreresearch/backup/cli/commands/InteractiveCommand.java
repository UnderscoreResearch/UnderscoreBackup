package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.configuration.CommandLineModule.DEVELOPER_MODE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;

import java.io.File;
import java.io.FileWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.cli.UIManager;
import com.underscoreresearch.backup.cli.web.ConfigurationPost;
import com.underscoreresearch.backup.cli.web.WebServer;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.PublicKeyEncrypion;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.model.BackupConfiguration;

@CommandPlugin(value = "interactive", description = "Run interactive interface",
        needConfiguration = false, needPrivateKey = false, readonlyRepository = false)
@Slf4j
public class InteractiveCommand extends Command {
    public void executeCommand(CommandLine commandLine) throws Exception {
        if (commandLine.getArgList().size() > 1) {
            throw new ParseException("Too many arguments for command");
        }

        WebServer server = InstanceFactory.getInstance(WebServer.class);
        server.start(InstanceFactory.getInstance(CommandLine.class).hasOption(DEVELOPER_MODE));

        UIManager.setup();

        try {
            BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);
            File file = new File(InstanceFactory.getInstance(MANIFEST_LOCATION), "server.pid");
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                writer.write(ManagementFactory.getRuntimeMXBean().getPid() + "\n");
            }
            file.deleteOnExit();

            try {
                InstanceFactory.getInstance(PublicKeyEncrypion.class);
            } catch (Exception exc) {
                if (configuration.getManifest() != null
                        && configuration.getManifest().getInteractiveBackup() != null
                        && configuration.getManifest().getInteractiveBackup()) {
                    log.warn("Resetting interactive backup parameter because key is missing");
                    ConfigurationPost.updateConfiguration(InstanceFactory.getInstance(CommandLineModule.CONFIG_DATA),
                            true, true);
                    InstanceFactory.reloadConfiguration(null, null);
                }
                throw exc;
            }
        } catch (Exception exc) {
            if (!commandLine.hasOption(DEVELOPER_MODE)) {
                server.launchPage();
            }
        }

        startBackupIfAvailable();

        Thread.sleep(Long.MAX_VALUE);
    }

    public static void startBackupIfAvailable() {
        if (InstanceFactory.getAdditionalSource() == null && InstanceFactory.hasConfiguration(false)) {
            BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);
            if (configuration.getSets().size() > 0
                    && configuration.getManifest().getInteractiveBackup() != null
                    && configuration.getManifest().getInteractiveBackup()) {
                try {
                    InstanceFactory.getInstance(MetadataRepository.class).open(false);
                    BackupCommand.executeBackup(true);
                } catch (Exception exc) {
                    log.error("Failed to start backup", exc);
                }
            }
        }
    }
}
