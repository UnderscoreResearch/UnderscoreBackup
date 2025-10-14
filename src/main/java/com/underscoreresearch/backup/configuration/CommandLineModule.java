package com.underscoreresearch.backup.configuration;

import com.google.common.base.Strings;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import com.underscoreresearch.backup.ui.web.WebServer;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupManifest;
import com.underscoreresearch.backup.service.api.BackupApi;
import com.underscoreresearch.backup.service.api.model.ListSourcesResponse;
import com.underscoreresearch.backup.service.api.model.SourceResponse;
import com.underscoreresearch.backup.utils.log.ActivityAppender;
import com.underscoreresearch.backup.utils.log.StateLogger;
import com.underscoreresearch.backup.machinestate.LinuxState;
import com.underscoreresearch.backup.machinestate.MachineState;
import com.underscoreresearch.backup.machinestate.OsxState;
import com.underscoreresearch.backup.machinestate.WindowsState;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.SystemUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

import static com.underscoreresearch.backup.configuration.EncryptionModule.DEFAULT_KEY_FILES;
import static com.underscoreresearch.backup.io.IOUtils.createDirectory;
import static com.underscoreresearch.backup.utils.log.LogUtil.formatTimestamp;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_READER;
import static java.lang.System.getenv;
import static java.lang.System.setErr;
import static java.util.prefs.Preferences.systemRoot;

/**
 * Guice module for handling command line arguments and configuration.
 * This module provides bindings for command line options, configuration files,
 * and various system paths used by the application.
 */
@Slf4j
public class CommandLineModule extends AbstractModule {
    /**
     * Location of the configuration file.
     */
    public static final String CONFIG_FILE_LOCATION = "CONFIG_FILE_LOCATION";

    /**
     * Command line option for private key seed/password.
     */
    public static final String PRIVATE_KEY_SEED = "password";
    
    /**
     * Named constant for key file name.
     */
    public static final String KEY_FILE_NAME = "key-file-name";
    
    /**
     * Command line option for encryption key data.
     */
    public static final String ENCRYPTION_KEY_DATA = "encryption-key-data";
    
    /**
     * Command line option for configuration data.
     */
    public static final String CONFIG_DATA = "config-data";
    
    /**
     * Command line option to disable logging to file.
     */
    public static final String NO_LOG = "no-log";
    
    /**
     * Command line option for log file location.
     */
    public static final String LOG_FILE = "log-file";
    
    /**
     * Command line option for key file location.
     */
    public static final String KEY = "key";
    
    /**
     * Command line option for debug mode.
     */
    public static final String DEBUG = "debug";
    
    /**
     * Command line option to force operations.
     */
    public static final String FORCE = "force";
    
    /**
     * Command line option for configuration file location.
     */
    public static final String CONFIG = "config";
    
    /**
     * Command line option for developer mode.
     */
    public static final String DEVELOPER_MODE = "developer-mode";
    
    /**
     * Command line option for human readable output.
     */
    public static final String HUMAN_READABLE = "human-readable";
    
    /**
     * Command line option to display full paths.
     */
    public static final String FULL_PATH = "full-path";
    
    /**
     * Command line option to include deleted files.
     */
    public static final String INCLUDE_DELETED = "include-deleted";
    
    /**
     * Command line option for recursive operations.
     */
    public static final String RECURSIVE = "recursive";
    
    /**
     * Command line option to overwrite existing files.
     */
    public static final String OVER_WRITE = "over-write";
    
    /**
     * Command line option to skip permission restoration.
     */
    public static final String SKIP_PERMISSIONS = "skip-permissions";
    
    /**
     * Command line option for timestamp specification.
     */
    public static final String TIMESTAMP = "timestamp";
    
    /**
     * Command line option for web server bind address.
     */
    public static final String BIND_ADDRESS = "bind-address";
    
    /**
     * Command line option for additional key generation.
     */
    public static final String ADDITIONAL_KEY = "additional-key";
    
    /**
     * Command line option for source specification.
     */
    public static final String SOURCE = "source";
    
    /**
     * Named constant for URL location.
     */
    public static final String URL_LOCATION = "URL_LOCATION";
    
    /**
     * Named constant for identity location.
     */
    public static final String IDENTITY_LOCATION = "IDENTITY_LOCATION";
    
    /**
     * Named constant for installation identity.
     */
    public static final String INSTALLATION_IDENTITY = "INSTALLATION_IDENTITY";
    
    /**
     * Command line option for manifest location.
     */
    public static final String MANIFEST_LOCATION = "manifest-location";
    
    /**
     * Command line option to prevent deletion.
     */
    public static final String NO_DELETE = "no-delete";
    
    /**
     * Named constant for default user manifest location.
     */
    public static final String DEFAULT_USER_MANIFEST_LOCATION = "DEFAULT_USER_MANIFEST_LOCATION";
    
    /**
     * Named constant for default manifest location.
     */
    public static final String DEFAULT_MANIFEST_LOCATION = "DEFAULT_MANIFEST_LOCATION";
    
    /**
     * Named constant for additional source.
     */
    public static final String ADDITIONAL_SOURCE = "ADDITIONAL_SOURCE";
    
    /**
     * Named constant for additional source name.
     */
    public static final String ADDITIONAL_SOURCE_NAME = "ADDITIONAL_SOURCE_NAME";
    
    /**
     * Named constant for source configuration.
     */
    public static final String SOURCE_CONFIG = "SOURCE_CONFIG";
    
    /**
     * Named constant for service mode.
     */
    public static final String SERVICE_MODE = "SERVICE_MODE";
    
    /**
     * Named constant for source configuration location.
     */
    public static final String SOURCE_CONFIG_LOCATION = "SOURCE_CONFIG_LOCATION";
    
    /**
     * Named constant for notification location.
     */
    public static final String NOTIFICATION_LOCATION = "NOTIFICATION_LOCATION";
    
    /**
     * Named constant for service data location.
     */
    private static final String SERVICE_DATA_LOCATION = "SERVICE_DATA_LOCATION";
    
    /**
     * Default configuration file path.
     */
    private static final String DEFAULT_CONFIG = "/etc/underscorebackup/config.json";
    
    /**
     * Default local cache path.
     */
    private static final String DEFAULT_LOCAL_PATH = "/var/cache/underscorebackup";
    
    /**
     * Default log file path.
     */
    private static final String DEFAULT_LOG_PATH = "/var/log/underscorebackup.log";
    
    /**
     * Flag to track if administrator notification has been shown.
     */
    private static boolean notifyAdministrator;
    
    /**
     * Command line arguments.
     */
    private final String[] argv;
    
    /**
     * Source identifier.
     */
    private final String source;
    
    /**
     * Source name.
     */
    private String sourceName;
    
    /**
     * Source definition from service.
     */
    private SourceResponse sourceDefinition;

    /**
     * Constructor for CommandLineModule.
     *
     * @param argv Command line arguments
     * @param source Source identifier (can be null)
     * @param sourceName Source name (can be null)
     */
    public CommandLineModule(String[] argv, String source, String sourceName) {
        this.argv = argv;
        if (source != null) {
            this.source = source;
            this.sourceName = Objects.requireNonNullElse(sourceName, source);
        } else {
            this.source = "";
            this.sourceName = "";
        }
    }

    /**
     * Parses a timestamp from command line arguments.
     *
     * @param commandLine The command line
     * @return The parsed timestamp or null if not specified
     * @throws ParseException If the timestamp cannot be parsed
     */
    public static Long timestamp(CommandLine commandLine) throws ParseException {
        if (!commandLine.hasOption(TIMESTAMP)) {
            return null;
        }
        Parser parser = new Parser();
        for (DateGroup group : parser.parse(commandLine.getOptionValue(TIMESTAMP))) {
            if (!group.getDates().isEmpty()) {
                long date = group.getDates().getFirst().getTime();
                if (date > Instant.now().toEpochMilli()) {
                    log.warn("Specified date in future, using current time");
                    return null;
                }
                log.info("Using timestamp " + formatTimestamp(date));
                return date;
            }
        }
        throw new ParseException("Failed to derive date from parameter: \"" + commandLine.getOptionValue(TIMESTAMP) + "\"");
    }

    /**
     * Checks if the application is running with administrator privileges.
     *
     * @return true if running as administrator, false otherwise
     */
    private static boolean isRunningAsAdministrator() {
        Preferences preferences = systemRoot();

        synchronized (System.err) {
            setErr(new PrintStream(new OutputStream() {
                @Override
                public void write(int b) {
                }
            }));

            try {
                preferences.put("foo", "bar");
                preferences.remove("foo");
                preferences.flush();
                return true;
            } catch (Exception exception) {
                return false;
            } finally {
                setErr(System.err);
            }
        }
    }

    /**
     * Gets the default user manifest location based on the operating system.
     *
     * @return The default user manifest location path
     */
    public static String getDefaultUserManifestLocation() {
        File userDir = new File(System.getProperty("user.home"));
        File configDir;
        if (SystemUtils.IS_OS_WINDOWS) {
            if (isRunningAsAdministrator()) {
                final String systemRoot = getenv("SYSTEMROOT");
                if (!Strings.isNullOrEmpty(systemRoot)) {
                    configDir = new File(systemRoot, "System32\\config\\systemprofile\\AppData\\Local\\UnderscoreBackup");
                    if (configDir.exists() && configDir.canRead() && configDir.canWrite()) {
                        if (!notifyAdministrator && InstanceFactory.isInitialized()) {
                            log.info("Using system profile directory since running as administrator");
                            notifyAdministrator = true;
                        }
                        return configDir.getAbsolutePath();
                    }
                }
            }

            String localAppData = System.getenv("LOCALAPPDATA");
            if (Strings.isNullOrEmpty(localAppData)) {
                localAppData = new File(userDir, "AppData\\Local").toString();
            }
            configDir = new File(localAppData, "UnderscoreBackup");
        } else {
            configDir = new File(userDir, ".underscoreBackup");
        }
        createDirectory(configDir, false);
        return configDir.getAbsolutePath();
    }

    /**
     * Gets the key file name for a specific source or the default key file.
     *
     * @param source The source identifier (can be null or empty)
     * @return The path to the key file
     */
    public static String getKeyFileName(String source) {
        if (!Strings.isNullOrEmpty(source)) {
            return Paths.get(InstanceFactory.getInstance(MANIFEST_LOCATION), "sources", source, "key").toString();
        } else {
            return InstanceFactory.getInstance(KEY_FILE_NAME);
        }
    }

    /**
     * Expands the source manifest destination with the provided destination.
     * This method ensures that the source configuration has a manifest destination
     * that matches the provided destination or adds a new one if needed.
     *
     * @param sourceConfig The source backup configuration
     * @param destination The destination to use for the manifest
     * @return The updated backup configuration
     */
    public static BackupConfiguration expandSourceManifestDestination(BackupConfiguration sourceConfig,
                                                                      BackupDestination destination) {
        final AtomicReference<BackupDestination> manifestDestination = new AtomicReference<>();
        if (sourceConfig.getManifest() == null) {
            sourceConfig.setManifest(new BackupManifest());
        }

        if (sourceConfig.getManifest().getDestination() != null) {
            manifestDestination.set(sourceConfig.getDestinations().get(sourceConfig.getManifest().getDestination()));
        } else {
            // Do we have a destination there that exactly match already?
            for (Map.Entry<String, BackupDestination> entry : sourceConfig.getDestinations().entrySet()) {
                if (entry.getValue().equals(destination)) {
                    manifestDestination.set(entry.getValue());
                    sourceConfig.getManifest().setDestination(entry.getKey());
                    break;
                }
            }
        }

        // So this is a bit tricky. If the configuration for the manifest matches the URI and type we have then
        // replaced the destination with what is defined in the backup configuration. Otherwise, add a new destination
        // with a generated name and make that the manifest location.
        if (manifestDestination.get() == null
                || !Objects.equals(destination.getEndpointUri(), manifestDestination.get().getEndpointUri())
                || !Objects.equals(destination.getType(), manifestDestination.get().getType())) {
            String manifestName = "manifest";
            int i = 0;
            while (sourceConfig.getDestinations().containsKey(manifestName)) {
                i++;
                manifestName = "manifest-" + i;
            }
            sourceConfig.getDestinations().put(manifestName, destination);
            sourceConfig.getManifest().setDestination(manifestName);
        } else {
            sourceConfig.getDestinations().put(sourceConfig.getManifest().getDestination(), destination);
        }

        return sourceConfig;
    }

    /**
     * Gets the source configuration file location path.
     *
     * @param manifestLocation The manifest location
     * @param source The source identifier
     * @return The path to the source configuration file
     */
    public static String getSourceConfigLocation(String manifestLocation, String source) {
        return Paths.get(manifestLocation, "sources", source, "config.json").toString();
    }

    /**
     * Provides the command line options for the application.
     *
     * @return The configured Options object with all supported command line options
     */
    @Provides
    @Singleton
    public Options options() {
        Options options = new Options();

        options.addOption(null, SOURCE, true, "Operate on a additional source");
        options.addOption("f", FORCE, false, "Force running command regardless of validation errors");
        options.addOption(null, CONFIG_DATA, true, "Configuration data");
        options.addOption("d", DEBUG, false, "Enable verbose debugging");
        options.addOption(null, DEVELOPER_MODE, false, "Developer mode");
        options.addOption(null, BIND_ADDRESS, true, "Specify the address to bind UI webserver to (Default localhost)");
        options.addOption(null, NO_DELETE, false, "Rebuild or refresh repository without deleting any old data");
        options.addOption("h", HUMAN_READABLE, false, "Display human readable sizes");
        options.addOption("R", RECURSIVE, false, "Process restore or list operation recursively");
        options.addOption(null, FULL_PATH, false, "Display full path");
        options.addOption("o", OVER_WRITE, false, "Overwrite existing files when restoring");
        options.addOption(null, SKIP_PERMISSIONS, false, "Don't restore file permissions");
        options.addOption("t", TIMESTAMP, true, "Timestamp to use for restore operations");
        options.addOption(null, INCLUDE_DELETED, false, "Include deleted files from repository");
        options.addOption(null, LOG_FILE, true, "Log file location");
        options.addOption(null, NO_LOG, false, "Don't write to a log file");
        options.addOption("c", CONFIG, true, "Location for configuration file");
        options.addOption("m", MANIFEST_LOCATION, true, "Local manifest location");
        options.addOption("k", KEY, true, "Location for key file");
        options.addOption(null, ENCRYPTION_KEY_DATA, true, "Encryption key data");
        options.addOption(null, PRIVATE_KEY_SEED, true, "Private key password");
        options.addOption(null, ADDITIONAL_KEY, false, "Generate a new additional key for sharing instead of a new master key");

        return options;
    }

    /**
     * Provides the parsed command line.
     *
     * @param options The command line options
     * @return The parsed command line
     * @throws ParseException If command line parsing fails
     */
    @Provides
    @Singleton
    public CommandLine commandLine(Options options) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        return parser.parse(options, argv);
    }

    /**
     * Determines if debug mode is enabled.
     *
     * @param commandLine The parsed command line
     * @return True if debug mode is enabled, false otherwise
     * @throws ParseException If command line parsing fails
     */
    @Provides
    @Named(DEBUG)
    public boolean debug(CommandLine commandLine) throws ParseException {
        return commandLine.hasOption(DEBUG);
    }

    /**
     * Provides the machine state implementation based on the operating system.
     *
     * @param configuration The backup configuration
     * @return The appropriate MachineState implementation for the current OS
     */
    @Provides
    @Singleton
    public MachineState machineState(BackupConfiguration configuration) {
        boolean pauseOnBattery = true;
        if (configuration.getManifest() != null && configuration.getManifest().getPauseOnBattery() != null) {
            pauseOnBattery = configuration.getManifest().getPauseOnBattery();
        }

        if (SystemUtils.IS_OS_LINUX) {
            return new LinuxState(pauseOnBattery);
        }
        if (SystemUtils.IS_OS_WINDOWS) {
            return new WindowsState(pauseOnBattery);
        }
        if (SystemUtils.IS_OS_MAC_OSX) {
            return new OsxState(pauseOnBattery);
        }
        return new MachineState(pauseOnBattery);
    }

    /**
     * Gets the manifest location from command line or default.
     *
     * @param commandLine The parsed command line
     * @param defaultLocation The default manifest location
     * @return The manifest location path
     */
    @Provides
    @Singleton
    @Named(MANIFEST_LOCATION)
    public String getManifestLocation(CommandLine commandLine,
                                      @Named(DEFAULT_MANIFEST_LOCATION) String defaultLocation) {
        if (commandLine.hasOption(MANIFEST_LOCATION)) {
            return commandLine.getOptionValue(MANIFEST_LOCATION);
        }
        return defaultLocation;
    }

    /**
     * Gets the additional source identifier from command line or configuration.
     *
     * @param configuration The backup configuration
     * @param commandLine The parsed command line
     * @param serviceManager The service manager
     * @return The additional source identifier
     * @throws ParseException If source parsing fails
     * @throws IOException If service communication fails
     */
    @Provides
    @Singleton
    @Named(ADDITIONAL_SOURCE)
    public String getSource(BackupConfiguration configuration, CommandLine commandLine, ServiceManager serviceManager)
            throws ParseException, IOException {
        String additionalSource = calculateSource(commandLine);
        if (additionalSource != null) {
            if (configuration.getAdditionalSources() == null || !configuration.getAdditionalSources().containsKey(additionalSource)) {
                if (!Strings.isNullOrEmpty(sourceName)) {
                    return additionalSource;
                }
                if (serviceManager.getToken() != null) {
                    ListSourcesResponse serviceSources = serviceManager.call(null, BackupApi::listSources);
                    Optional<SourceResponse> service = serviceSources.getSources().stream()
                            .filter((source) -> source.getSourceId().equals(additionalSource)
                                    || source.getName().equals(additionalSource)).findAny();
                    if (service.isPresent()) {
                        sourceDefinition = service.get();
                        sourceName = service.get().getName();
                        return service.get().getSourceId();
                    }
                }
                throw new ParseException(String.format("Additional source \"%s\" not in config file", additionalSource));
            }
            return additionalSource;
        }
        return "";
    }

    /**
     * Provides the source definition from the service.
     *
     * @param ignored The additional source identifier (ignored)
     * @return The source response or an empty one if not defined
     */
    @Provides
    @Singleton
    public SourceResponse sourceDefinition(@Named(ADDITIONAL_SOURCE) String ignored) {
        if (sourceDefinition == null) {
            return new SourceResponse();
        }
        return sourceDefinition;
    }

    /**
     * Gets the additional source name.
     *
     * @param source The additional source identifier
     * @return The source name or the source identifier if not defined
     */
    @Provides
    @Singleton
    @Named(ADDITIONAL_SOURCE_NAME)
    public String getSourceName(@Named(ADDITIONAL_SOURCE) String source) {
        if (!Strings.isNullOrEmpty(sourceName)) {
            return sourceName;
        }
        return source;
    }

    /**
     * Determines if the application is running in service mode.
     *
     * @param commandLine The parsed command line
     * @param manifestLocation The manifest location
     * @return True if running in service mode, false otherwise
     */
    @Provides
    @Named(SERVICE_MODE)
    public boolean isService(CommandLine commandLine, @Named(MANIFEST_LOCATION) String manifestLocation) {
        if (!commandLine.getArgList().isEmpty()) {
            switch (commandLine.getArgList().get(0)) {
                case "gui" -> {
                    return true;
                }
                case "interactive" -> {
                    if (commandLine.getArgList().size() == 2) {
                        return commandLine.getArgList().get(1).equals("service");
                    }
                }
            }
        }
        return manifestLocation.equals(DEFAULT_LOCAL_PATH);
    }

    /**
     * Gets the service data location based on operating system and service mode.
     *
     * @param service True if running in service mode
     * @param manifestLocation The manifest location
     * @return The service data location path
     */
    @Provides
    @Named(SERVICE_DATA_LOCATION)
    @Singleton
    public String serviceDataLocation(@Named(SERVICE_MODE) boolean service, @Named(MANIFEST_LOCATION) String manifestLocation) {
        if (service) {
            if (SystemUtils.IS_OS_WINDOWS) {
                String programData = System.getenv("ProgramData");
                if (Strings.isNullOrEmpty(programData)) {
                    programData = "C:\\ProgramData";
                }
                return Paths.get(programData, "UnderscoreBackup").toString();
            } else {
                return DEFAULT_LOCAL_PATH;
            }
        }
        return manifestLocation;
    }

    /**
     * Gets the notification location path.
     *
     * @param location The service data location
     * @return The notification location path
     */
    @Provides
    @Named(NOTIFICATION_LOCATION)
    @Singleton
    public String notificationLocation(@Named(SERVICE_DATA_LOCATION) String location) {
        File file = Paths.get(location, "notifications").toFile();
        createDirectory(file, false);
        return file.toString();
    }

    /**
     * Calculates the source identifier from command line or instance.
     *
     * @param commandLine The parsed command line
     * @return The source identifier or null if not specified
     */
    private String calculateSource(CommandLine commandLine) {
        if (!Strings.isNullOrEmpty(source)) {
            return source;
        }
        if (commandLine.hasOption(SOURCE)) {
            return commandLine.getOptionValue(SOURCE);
        }
        return null;
    }

    /**
     * Gets the URL location for configuration.
     *
     * @param location The service data location
     * @return The URL location path
     */
    @Provides
    @Singleton
    @Named(URL_LOCATION)
    public String getUrlLocation(@Named(SERVICE_DATA_LOCATION) String location) {
        try {
            return new File(location, "configuration.url").getCanonicalPath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the identity location path.
     *
     * @param manifestLocation The manifest location
     * @return The identity location path
     */
    @Provides
    @Singleton
    @Named(IDENTITY_LOCATION)
    public String getIdentityLocation(@Named(MANIFEST_LOCATION) String manifestLocation) {
        try {
            return new File(manifestLocation, "identity").getCanonicalPath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets or creates the installation identity.
     *
     * @param identityLocation The identity file location
     * @param machineState The machine state
     * @return The installation identity UUID
     */
    @Provides
    @Singleton
    @Named(INSTALLATION_IDENTITY)
    public String getInstallationIdentity(@Named(IDENTITY_LOCATION) String identityLocation,
                                          MachineState machineState) {
        File file = new File(identityLocation);
        if (!file.exists()) {
            String identity = UUID.randomUUID().toString();
            try {
                try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                    writer.write(identity);
                }
                machineState.setOwnerOnlyPermissions(file);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create identity file", e);
            }
            return identity;
        }
        try (FileInputStream reader = new FileInputStream(file)) {
            return new String(IOUtils.readAllBytes(reader), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read identity file", e);
        }
    }

    /**
     * Gets the backup configuration file location.
     *
     * @param commandLine The parsed command line
     * @return The configuration file path
     */
    @Named(CONFIG_FILE_LOCATION)
    @Singleton
    @Provides
    public String backupConfigurationLocation(CommandLine commandLine) {
        if (commandLine.hasOption(CONFIG)) {
            return commandLine.getOptionValue(CONFIG);
        }

        if (SystemUtils.IS_OS_WINDOWS) {
            return new File(InstanceFactory.getInstance(DEFAULT_USER_MANIFEST_LOCATION), "config.json")
                    .getAbsolutePath();
        } else {
            if (!commandLine.getArgList().isEmpty() && commandLine.getArgList().getFirst().equals("interactive")) {
                File systemDir = new File("/etc/underscorebackup");
                createDirectory(systemDir, false);

                if (Files.isWritable(systemDir.toPath())) {
                    return DEFAULT_CONFIG;
                }
                return new File(defaultUserManifestLocation(), "config.json")
                        .getAbsolutePath();
            }
            if (new File(DEFAULT_CONFIG).canRead()) {
                return DEFAULT_CONFIG;
            }
            return new File(defaultUserManifestLocation(), "config.json")
                    .getAbsolutePath();
        }
    }

    /**
     * Provides the default user manifest location.
     *
     * @return The default user manifest location path
     */
    @Named(DEFAULT_USER_MANIFEST_LOCATION)
    @Provides
    @Singleton
    public String defaultUserManifestLocation() {
        return getDefaultUserManifestLocation();
    }

    /**
     * Provides the default manifest location based on configuration and system.
     *
     * @param commandLine The parsed command line
     * @return The default manifest location path
     */
    @Named(DEFAULT_MANIFEST_LOCATION)
    @Provides
    @Singleton
    public String defaultManifestLocation(CommandLine commandLine) {
        if (commandLine.hasOption(CONFIG)) {
            try {
                File file = new File(commandLine.getOptionValue(CONFIG)).getParentFile().getCanonicalFile();
                createDirectory(file, false);
                return file.getAbsolutePath();
            } catch (IOException exc) {
                log.warn("Failed to resolve default manifest location from config location \"{}\"",
                        commandLine.getOptionValue(CONFIG));
            }
        }

        if (!SystemUtils.IS_OS_WINDOWS) {
            File systemFile = new File(DEFAULT_LOCAL_PATH);
            createDirectory(systemFile, false);
            if (Files.isWritable(systemFile.toPath())) {
                return DEFAULT_LOCAL_PATH;
            }
        }
        return defaultUserManifestLocation();
    }

    /**
     * Provides the web server instance.
     *
     * @return The web server instance
     */
    @Provides
    @Singleton
    public WebServer webServer() {
        return WebServer.getInstance();
    }

    /**
     * Provides the backup configuration data from file or command line.
     *
     * @param commandLine The parsed command line
     * @param configFile The configuration file path
     * @return The backup configuration data as a string
     * @throws IOException If reading the configuration file fails
     */
    @Named(CONFIG_DATA)
    @Singleton
    @Provides
    public String backupConfiguration(CommandLine commandLine,
                                      @Named(CONFIG_FILE_LOCATION) String configFile) throws IOException {
        if (commandLine.hasOption(CONFIG)) {
            if (commandLine.hasOption(CONFIG_DATA)) {
                throw new IllegalArgumentException("Can't specify both config file and config data");
            }
        } else {
            if (commandLine.hasOption(CONFIG_DATA)) {
                return commandLine.getOptionValue(CONFIG_DATA);
            }
        }
        File file = new File(configFile);
        if (file.exists()) {
            try (FileInputStream reader = new FileInputStream(file)) {
                return new String(IOUtils.readAllBytes(reader), StandardCharsets.UTF_8);
            }
        } else {
            return "{}";
        }
    }

    /**
     * Parses the backup configuration from JSON data.
     *
     * @param configData The configuration data as a string
     * @return The parsed backup configuration
     * @throws IOException If parsing the configuration fails
     */
    @Provides
    @Singleton
    public BackupConfiguration backupConfiguration(@Named(CONFIG_DATA) String configData)
            throws IOException {
        return BACKUP_CONFIGURATION_READER.readValue(configData);
    }

    /**
     * Gets the source configuration file location.
     *
     * @param manifestLocation The manifest location
     * @param source The source identifier
     * @return The source configuration file path
     */
    @Provides
    @Singleton
    @Named(SOURCE_CONFIG_LOCATION)
    public String sourceConfigLocation(@Named(MANIFEST_LOCATION) String manifestLocation, @Named(ADDITIONAL_SOURCE) String source) {
        return getSourceConfigLocation(manifestLocation, source);
    }

    /**
     * Provides the source-specific backup configuration.
     *
     * @param configuration The main backup configuration
     * @param additionalSource The additional source identifier
     * @param configLocation The source configuration file location
     * @return The source-specific backup configuration
     * @throws IOException If reading or parsing the configuration fails
     */
    @Provides
    @Singleton
    @Named(SOURCE_CONFIG)
    public BackupConfiguration backupConfiguration(BackupConfiguration configuration,
                                                   @Named(ADDITIONAL_SOURCE) String additionalSource,
                                                   @Named(SOURCE_CONFIG_LOCATION) String configLocation) throws IOException {
        if (!Strings.isNullOrEmpty(additionalSource)) {
            BackupConfiguration sourceConfig
                    = BACKUP_CONFIGURATION_READER.readValue(new File(configLocation));
            BackupDestination destination;
            if (configuration.getAdditionalSources() != null) {
                destination = configuration.getAdditionalSources().get(additionalSource);
                if (destination != null) {
                    return expandSourceManifestDestination(sourceConfig, destination);
                }
            }
            return sourceConfig;
        }
        return configuration;
    }

    /**
     * Provides the encryption key data from command line.
     *
     * @param commandLine The parsed command line
     * @return The encryption key data or empty string if not specified
     */
    @Provides
    @Singleton
    @Named(ENCRYPTION_KEY_DATA)
    public String publicKeyData(CommandLine commandLine) {
        if (commandLine.hasOption(ENCRYPTION_KEY_DATA))
            return commandLine.getOptionValue(ENCRYPTION_KEY_DATA);
        return "";
    }

    /**
     * Gets the log file path based on command line options and system.
     *
     * @param commandLine The parsed command line
     * @return The log file path or empty string if logging is disabled
     */
    @Provides
    @Singleton
    @Named(LOG_FILE)
    public String getLogFile(CommandLine commandLine) {
        if (commandLine.hasOption(NO_LOG)) {
            return "";
        }
        if (commandLine.hasOption(LOG_FILE)) {
            return commandLine.getOptionValue(LOG_FILE);
        }

        if (!SystemUtils.IS_OS_WINDOWS) {
            File systemLogFile = new File(DEFAULT_LOG_PATH);
            if (systemLogFile.exists() && systemLogFile.canWrite()) {
                return DEFAULT_LOG_PATH;
            }
            try {
                new FileOutputStream(systemLogFile).close();
                return DEFAULT_LOG_PATH;
            } catch (IOException exc) {
                // We don't have access default to user log file.
            }
        }

        return new File(getDefaultUserManifestLocation(), "underscorebackup.log").getAbsolutePath();
    }

    /**
     * Gets the key file name based on command line options or default locations.
     *
     * @param commandLine The parsed command line
     * @return The key file path
     * @throws ParseException If no key file can be found
     */
    @Provides
    @Singleton
    @Named(KEY_FILE_NAME)
    public String getKeyFileName(CommandLine commandLine)
            throws ParseException {
        String keyFile = commandLine.getOptionValue(KEY);
        if (Strings.isNullOrEmpty(keyFile)) {
            if (commandLine.hasOption(PRIVATE_KEY_SEED))
                return DEFAULT_KEY_FILES[0];

            for (String fileName : DEFAULT_KEY_FILES) {
                File file = new File(fileName);
                if (file.isFile() && file.canRead()) {
                    keyFile = fileName;
                }
            }
        }
        if (Strings.isNullOrEmpty(keyFile)) {
            throw new ParseException("Could not find a key file. Try using the --generate-key option");
        }
        return keyFile;
    }

    /**
     * Provides the state logger instance.
     *
     * @param debug True if debug mode is enabled
     * @return The state logger instance
     */
    @Provides
    @Singleton
    public StateLogger stateLogger(@Named(DEBUG) boolean debug) {
        return new StateLogger(debug);
    }

    /**
     * Provides the activity appender for logging.
     *
     * @return The activity appender instance
     */
    @Provides
    @Singleton
    public ActivityAppender activityAppender() {
        return ActivityAppender.createAppender("Activity", null, null);
    }

    /**
     * Provides the service manager instance.
     *
     * @param location The manifest location
     * @return The service manager instance
     * @throws IOException If creating the service manager fails
     */
    @Provides
    @Singleton
    public ServiceManager serviceManager(@Named(MANIFEST_LOCATION) String location) throws IOException {
        return new ServiceManagerImpl(location);
    }
}
