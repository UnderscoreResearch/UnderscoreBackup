package com.underscoreresearch.backup.ui.commands;

import com.google.common.base.Stopwatch;
import com.underscoreresearch.backup.ui.desktop.FileUIManager;
import com.underscoreresearch.backup.ui.desktop.UIHandler;
import com.underscoreresearch.backup.ui.web.methods.ConfigurationPost;
import com.underscoreresearch.backup.ui.web.WebServer;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.RepositoryOpenMode;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.service.SubscriptionLackingException;
import com.underscoreresearch.backup.utils.log.StateLogger;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.underscoreresearch.backup.ui.commands.ConfigureCommand.getConfigurationUrl;
import static com.underscoreresearch.backup.ui.commands.ConfigureCommand.validateConfigurationUrl;
import static com.underscoreresearch.backup.configuration.CommandLineModule.DEVELOPER_MODE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.utils.log.LogUtil.debug;

/**
 * Command for running the interactive web interface for the backup system.
 * This command starts a web server for configuration and monitoring, and optionally
 * starts the backup process if configured to do so.
 */
@CommandPlugin(value = "interactive", description = "Run interactive interface",
        needConfiguration = false, needPrivateKey = false, readonlyRepository = false,
        preferNice = true)
@Slf4j
public class InteractiveCommand extends Command {
    /**
     * Starts the backup process if it's available and configured to run.
     * This checks if a valid configuration and encryption key are available,
     * and if interactive backup is enabled in the configuration.
     */
    public static void startBackupIfAvailable() {
        if (InstanceFactory.getAdditionalSource() == null && InstanceFactory.hasConfiguration(false)) {
            BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);
            try {
                InstanceFactory.getInstance(EncryptionIdentity.class);
            } catch (Exception exc) {
                log.info("No encryption key available");
                return;
            }
            if (!configuration.getSets().isEmpty()
                    && configuration.getManifest().getInteractiveBackup() != null
                    && configuration.getManifest().getInteractiveBackup()) {
                try {
                    InstanceFactory.getInstance(MetadataRepository.class).open(RepositoryOpenMode.READ_WRITE);
                    BackupCommand.executeBackup(true);
                } catch (Exception exc) {
                    log.error("Failed to start backup", exc);
                }
            } else {
                initializeManifest();
            }
        }
    }

    /**
     * Initializes the manifest manager in a separate thread.
     */
    private static void initializeManifest() {
        AtomicBoolean started = new AtomicBoolean(false);
        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
        Thread thread = new Thread(() -> initializeManifest(started, manifestManager),
                "InitializeManifest");
        thread.setDaemon(true);

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
                Thread.currentThread().interrupt();
                log.warn("Failed to wait", e);
            }
        }
    }

    /**
     * Initializes the manifest manager, waiting for internet connectivity if needed.
     *
     * @param started Flag indicating whether initialization has started
     * @param manifestManager The manifest manager to initialize
     */
    private static void initializeManifest(AtomicBoolean started, ManifestManager manifestManager) {
        try {
            started.set(true);
            IOUtils.waitForInternet(() -> {
                manifestManager.initialize(InstanceFactory.getInstance(LogConsumer.class), true);
                return null;
            }, true);
        } catch (Exception e) {
            if (e.getCause() instanceof SubscriptionLackingException subscriptionLackingException) {
                log.error(subscriptionLackingException.getMessage() + " Failed to initialize manifest");
            } else {
                log.error("Failed to initialize manifest", e);
            }
        }
    }

    /**
     * Checks if another instance of the application is already running.
     *
     * @return True if another instance is running, false otherwise
     */
    private static boolean checkIfAlreadyRunning() {
        try {
            String url = getConfigurationUrl();
            validateConfigurationUrl(url);
            log.info("Underscore Backup is already running, shutting down");
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Checks if automatic browser opening should be suppressed.
     *
     * @return True if browser opening should be suppressed, false otherwise
     */
    public static boolean suppressedOpen() {
        return "TRUE".equals(System.getenv("UNDERSCORE_SUPPRESS_OPEN"));
    }

    /**
     * Executes the interactive command, starting the web server and optionally the backup process.
     *
     * @param commandLine The parsed command line arguments
     * @throws Exception If an error occurs during command execution
     */
    public void executeCommand(CommandLine commandLine) throws Exception {
        boolean lock = false;
        boolean service = false;
        if (commandLine.getArgList().size() == 2) {
            switch (commandLine.getArgList().get(1)) {
                case "lock" -> lock = true;
                case "service" -> service = true;
                default -> throw new ParseException("Invalid parameters");
            }
        } else if (commandLine.getArgList().size() > 1) {
            throw new ParseException("Too many arguments for command");
        }

        if (checkIfAlreadyRunning())
            return;

        WebServer server = InstanceFactory.getInstance(WebServer.class);
        server.start(InstanceFactory.getInstance(CommandLine.class).hasOption(DEVELOPER_MODE));

        if (service)
            UIHandler.setup(new FileUIManager());
        else
            UIHandler.setup();

        try {
            BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);
            File file = new File(InstanceFactory.getInstance(MANIFEST_LOCATION), "server.pid");
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                writer.write(ManagementFactory.getRuntimeMXBean().getPid() + "\n");
            }
            file.deleteOnExit();

            try {
                InstanceFactory.getInstance(EncryptionIdentity.class);
            } catch (Exception exc) {
                if (configuration.getManifest() != null
                        && configuration.getManifest().getInteractiveBackup() != null
                        && configuration.getManifest().getInteractiveBackup()) {
                    log.warn("Resetting interactive backup parameter because key is missing");
                    ConfigurationPost.updateConfiguration(InstanceFactory.getInstance(CommandLineModule.CONFIG_DATA),
                            true, true, true);
                    InstanceFactory.reloadConfiguration(null);
                }
                throw exc;
            }
            if (configuration.getSets() == null
                    || configuration.getSets().isEmpty()
                    || configuration.getSets().stream().allMatch(set -> set.getRoots().isEmpty())) {
                throw new ParseException("No backup sets configured");
            }
        } catch (Exception exc) {
            if (!commandLine.hasOption(DEVELOPER_MODE) && !suppressedOpen()) {
                server.launchPage();
            }
        }

        startBackupIfAvailable();

        waitForCompletion(lock);
    }

    /**
     * Waits for the command to complete, either by waiting indefinitely or by monitoring a lock file.
     *
     * @param lock Whether to use a lock file to determine when to exit
     * @throws InterruptedException If the thread is interrupted while waiting
     */
    private void waitForCompletion(boolean lock) throws InterruptedException {
        if (lock) {
            // On OSX we have a lock file that is locked by the UI application.
            Path path = Paths.get(InstanceFactory.getInstance(CommandLineModule.MANIFEST_LOCATION),
                    "notifications", "lock");

            try {
                try (RandomAccessFile file = new RandomAccessFile(path.toString(), "rw")) {
                    file.getChannel().lock().close();
                }
                log.info("Shutting down because of lock file");
            } catch (IOException exc) {
                log.warn("Shutting down because of lock file error", exc);
            }
            InstanceFactory.shutdown();
            InstanceFactory.waitForShutdown();
            log.info("Shutdown completed");
            System.exit(0);
        } else {
            Thread.sleep(Integer.MAX_VALUE);
        }
    }
}
