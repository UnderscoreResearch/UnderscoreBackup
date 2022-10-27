package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.configuration.CommandLineModule.DEVELOPER_MODE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.utils.LogUtil.debug;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import com.google.common.base.Stopwatch;
import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.cli.UIManager;
import com.underscoreresearch.backup.cli.web.ConfigurationPost;
import com.underscoreresearch.backup.cli.web.WebServer;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.utils.StateLogger;

@CommandPlugin(value = "interactive", description = "Run interactive interface",
        needConfiguration = false, needPrivateKey = false, readonlyRepository = false,
        preferNice = true)
@Slf4j
public class InteractiveCommand extends Command {
    public static void startBackupIfAvailable() {
        if (InstanceFactory.getAdditionalSource() == null && InstanceFactory.hasConfiguration(false)) {
            BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);
            try {
                InstanceFactory.getInstance(EncryptionKey.class);
            } catch (Exception exc) {
                log.info("No encryption key available");
                return;
            }
            if (configuration.getSets().size() > 0
                    && configuration.getManifest().getInteractiveBackup() != null
                    && configuration.getManifest().getInteractiveBackup()) {
                try {
                    InstanceFactory.getInstance(MetadataRepository.class).open(false);
                    BackupCommand.executeBackup(true);
                } catch (Exception exc) {
                    log.error("Failed to start backup", exc);
                }
            } else {
                initializeManifest();
            }
        }
    }

    private static void initializeManifest() {
        AtomicBoolean started = new AtomicBoolean(false);
        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
        Thread thread = new Thread(() -> initializeManifest(started, manifestManager),
                "InitializeManifest");

        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);

        InstanceFactory.addOrderedCleanupHook(() -> {
            started.set(false);
            debug(() -> log.debug("Shutdown initiated"));

            InstanceFactory.shutdown();

            try {
                repository.flushLogging();
                manifestManager.shutdown();
                repository.close();
            } catch (IOException e) {
                log.error("Failed to close manifest", e);
            }
            InstanceFactory.getInstance(StateLogger.class).reset();
            log.info("Shutdown completed");
        });

        thread.start();
        Stopwatch stopwatch = Stopwatch.createStarted();
        while ((!started.get() || stopwatch.elapsed(TimeUnit.MILLISECONDS) < 1000) && thread.isAlive()) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                log.warn("Failed to wait", e);
            }
        }
    }

    private static void initializeManifest(AtomicBoolean started, ManifestManager manifestManager) {
        try {
            started.set(true);
            IOUtils.waitForInternet(() -> {
                manifestManager.initialize(InstanceFactory.getInstance(LogConsumer.class), true);
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to initialize manifest", e);
        }
    }

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
                InstanceFactory.getInstance(EncryptionKey.class);
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
}
