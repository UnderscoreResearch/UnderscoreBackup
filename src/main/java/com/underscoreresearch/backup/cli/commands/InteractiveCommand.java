package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.configuration.CommandLineModule.DEVELOPER_MODE;

import java.io.File;
import java.io.FileWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;

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
public class InteractiveCommand extends SimpleCommand {

    public void executeCommand() throws Exception {
        WebServer server = InstanceFactory.getInstance(WebServer.class);
        server.start(InstanceFactory.getInstance(CommandLine.class).hasOption(DEVELOPER_MODE));

        UIManager.setup();

        try {
            BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);
            if (configuration.getManifest() != null && configuration.getManifest().getLocalLocation() != null) {
                File file = new File(configuration.getManifest().getLocalLocation(), "server.pid");
                try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                    writer.write(ManagementFactory.getRuntimeMXBean().getPid() + "\n");
                }
                file.deleteOnExit();
            }

            try {
                InstanceFactory.getInstance(PublicKeyEncrypion.class);
            } catch (Exception exc) {
                if (configuration.getManifest() != null
                        && configuration.getManifest().getInteractiveBackup() != null
                        && configuration.getManifest().getInteractiveBackup()) {
                    log.warn("Resetting interactive backup parameter because key is missing");
                    ConfigurationPost.updateConfiguration(InstanceFactory.getInstance(CommandLineModule.CONFIG_DATA),
                            true);
                    InstanceFactory.reloadConfiguration(null);
                }
                throw exc;
            }
        } catch (Exception exc) {
            server.launchPage();
        }

        startBackupIfAvailable();

        Thread.sleep(Long.MAX_VALUE);
    }

    public static void startBackupIfAvailable() throws Exception {
        if (InstanceFactory.hasConfiguration(false)) {
            BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);
            if (configuration.getSets().size() > 0
                    && configuration.getManifest().getInteractiveBackup() != null
                    && configuration.getManifest().getInteractiveBackup()) {
                InstanceFactory.getInstance(MetadataRepository.class).open(false);
                BackupCommand.executeBackup();
            }
        }
    }
}
