package com.underscoreresearch.backup.configuration;

import static com.underscoreresearch.backup.configuration.EncryptionModule.DEFAULT_KEY_FILES;
import static com.underscoreresearch.backup.utils.LogUtil.formatTimestamp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.SystemUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.base.Strings;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.web.WebServer;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.utils.ActivityAppender;
import com.underscoreresearch.backup.utils.StateLogger;
import com.underscoreresearch.backup.utils.state.LinuxState;
import com.underscoreresearch.backup.utils.state.MachineState;
import com.underscoreresearch.backup.utils.state.OsxState;
import com.underscoreresearch.backup.utils.state.WindowsState;

@Slf4j
public class CommandLineModule extends AbstractModule {
    public static final String NEED_PRIVATE_KEY = "NEED_PRIVATE_KEY";
    public static final String CONFIG_FILE_LOCATION = "CONFIG_FILE_LOCATION";

    public static final String PRIVATE_KEY_SEED = "passphrase";
    public static final String KEY_FILE_NAME = "key-file-name";
    public static final String PUBLIC_KEY_DATA = "public-key-data";
    public static final String CONFIG_DATA = "config-data";
    public static final String NO_LOG = "no-log";
    public static final String LOG_FILE = "log-file";
    public static final String KEY = "key";
    public static final String DEBUG = "debug";
    public static final String FORCE = "force";
    public static final String CONFIG = "config";
    public static final String DEVELOPER_MODE = "developer-mode";
    public static final String HUMAN_READABLE = "human-readable";
    public static final String FULL_PATH = "full-path";
    public static final String INCLUDE_DELETED = "include-deleted";
    public static final String RECURSIVE = "recursive";
    public static final String OVER_WRITE = "over-write";
    public static final String TIMESTAMP = "timestamp";
    public static final String BIND_ADDRESS = "bind-address";
    public static final String SOURCE = "source";
    public static final String URL_LOCATION = "URL_LOCATION";
    public static final String IDENTITY_LOCATION = "IDENTITY_LOCATION";
    public static final String INSTALLATION_IDENTITY = "INSTALLATION_IDENTITY";
    public static final String NO_DELETE_REBUILD = "no-delete-rebuild";

    private static final String DEFAULT_CONFIG = "/etc/underscorebackup/config.json";
    private static final String DEFAULT_LOCAL_PATH = "/var/cache/underscorebackup";
    private static final String DEFAULT_LOG_PATH = "/var/log/underscorebackup.log";

    private static final ObjectReader BACKUP_CONFIGURATION_READER
            = new ObjectMapper().readerFor(BackupConfiguration.class);
    public static final String DEFAULT_USER_MANIFEST_LOCATION = "DEFAULT_USER_MANIFEST_LOCATION";
    public static final String DEFAULT_MANIFEST_LOCATION = "DEFAULT_MANIFEST_LOCATION";
    public static final String MANIFEST_LOCATION = "MANIFEST_LOCATION";
    public static final String ADDITIONAL_SOURCE = "ADDITIONAL_SOURCE";
    public static final String SOURCE_CONFIG = "SOURCE_CONFIG";
    public static final String SOURCE_CONFIG_LOCATION = "SOURCE_CONFIG_LOCATION";

    private final String[] argv;
    private final String passphrase;
    private final String source;

    public CommandLineModule(String[] argv, String passphrase, String source) {
        this.argv = argv;
        this.passphrase = passphrase;
        if (source != null) {
            this.source = source;
        } else {
            this.source = "";
        }
    }

    @Provides
    @Singleton
    public Options options() {
        Options options = new Options();

        options.addOption("k", KEY, true, "Location for key file");
        options.addOption("d", DEBUG, false, "Enable verbose debugging");
        options.addOption("f", FORCE, false, "Force running command regardless of validation errors");
        options.addOption("c", CONFIG, true, "Location for configuration file");
        options.addOption(null, CONFIG_DATA, true, "Configuration data");
        options.addOption(null, PUBLIC_KEY_DATA, true, "Public key data");
        options.addOption(null, PRIVATE_KEY_SEED, true, "Private key passphrase");
        options.addOption(null, DEVELOPER_MODE, false, "Developer mode");
        options.addOption(null, LOG_FILE, true, "Log file location");
        options.addOption(null, INCLUDE_DELETED, false, "Include deleted files from repository");
        options.addOption(null, NO_LOG, false, "Don't write to a log file");
        options.addOption(null, BIND_ADDRESS, true, "Specify the address to bind UI webserver to (Default localhost)");
        options.addOption(null, NO_DELETE_REBUILD, false, "Rebuild repository without performing any deletes");
        options.addOption(null, SOURCE, true, "Operate on a additional source");
        options.addOption("h", HUMAN_READABLE, false, "Display human readable sizes");
        options.addOption("R", RECURSIVE, false, "Process restore or list operation recursively");
        options.addOption(null, FULL_PATH, false, "Display full path");
        options.addOption("o", OVER_WRITE, false, "Overwrite existing files when restoring");
        options.addOption("t", TIMESTAMP, true, "Timestamp to use for restore operations");

        return options;
    }

    @Provides
    @Singleton
    public CommandLine commandLine(Options options) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        CommandLine cl = parser.parse(options, argv);
        return cl;
    }

    @Provides
    @Named(DEBUG)
    public boolean debug(CommandLine commandLine) throws ParseException {
        return commandLine.hasOption(DEBUG);
    }

    public static Long timestamp(CommandLine commandLine) throws ParseException {
        if (!commandLine.hasOption(TIMESTAMP)) {
            return null;
        }
        Parser parser = new Parser();
        for (DateGroup group : parser.parse(commandLine.getOptionValue(TIMESTAMP))) {
            if (group.getDates().size() > 0) {
                long date = group.getDates().get(0).getTime();
                if (date > Instant.now().toEpochMilli()) {
                    log.warn("Specified date in future, using current time");
                    return null;
                }
                log.info("Using timestamp " + formatTimestamp(date));
                return date;
            }
        }
        throw new ParseException("Failed to derive date from parameter: " + commandLine.getOptionValue(TIMESTAMP));
    }

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

    @Provides
    @Singleton
    @Named(MANIFEST_LOCATION)
    public String getManifestLocation(BackupConfiguration configuration,
                                      @Named(DEFAULT_MANIFEST_LOCATION) String defaultLocation) {
        if (configuration.getManifest() != null
                && !Strings.isNullOrEmpty(configuration.getManifest().getLocalLocation())) {
            return configuration.getManifest().getLocalLocation();
        }
        return defaultLocation;
    }

    @Provides
    @Singleton
    @Named(ADDITIONAL_SOURCE)
    public String getSource(BackupConfiguration configuration, CommandLine commandLine) throws ParseException {
        String additionalSource = calculateSource(commandLine);
        if (additionalSource != null) {
            if (!configuration.getAdditionalSources().containsKey(additionalSource)) {
                throw new ParseException(String.format("Additional source %s not in config file", additionalSource));
            }
            return additionalSource;
        }
        return "";
    }

    private String calculateSource(CommandLine commandLine) {
        if (!Strings.isNullOrEmpty(source)) {
            return source;
        }
        if (commandLine.hasOption(SOURCE)) {
            return commandLine.getOptionValue(SOURCE);
        }
        return null;
    }

    @Provides
    @Singleton
    @Named(URL_LOCATION)
    public String getUrlLocation(@Named(MANIFEST_LOCATION) String manifestLocation) {
        try {
            if (SystemUtils.IS_OS_MAC_OSX) {
                return new File(System.getProperty("user.home"),
                        "Library/Containers/com.underscoreresearch.UnderscoreBackup.UI/Data/Applications/configuration.url")
                        .getCanonicalPath();
            }

            return new File(manifestLocation, "configuration.url").getCanonicalPath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

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

    @Provides
    @Singleton
    @Named(INSTALLATION_IDENTITY)
    public String getInstallationIdentity(@Named(IDENTITY_LOCATION) String identityLocation) {
        File file = new File(identityLocation);
        if (!file.exists()) {
            String identity = UUID.randomUUID().toString();
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                writer.write(identity);
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

    @Provides
    @Singleton
    @Named(NEED_PRIVATE_KEY)
    public boolean needPrivateKey(CommandLine commandLine) {
        if (commandLine.getArgList().size() > 0) {
            Class<? extends Command> commandClass = Command.findCommandClass(commandLine.getArgList().get(0));
            if (commandClass != null) {
                return Command.needPrivateKey(commandClass);
            }
        }
        return false;
    }

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
            if (commandLine.getArgList().size() > 0 && commandLine.getArgList().get(0).equals("interactive")) {
                File systemDir = new File("/etc/underscorebackup");
                if (!systemDir.exists()) {
                    systemDir.mkdir();
                }
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

    public static String getDefaultUserManifestLocation() {
        File userDir = new File(System.getProperty("user.home"));
        File configDir;
        if (SystemUtils.IS_OS_WINDOWS) {
            configDir = new File(userDir, "AppData\\Local\\UnderscoreBackup");
        } else {
            configDir = new File(userDir, ".underscoreBackup");
        }
        configDir.mkdirs();
        return configDir.getAbsolutePath();
    }

    @Named(DEFAULT_USER_MANIFEST_LOCATION)
    @Provides
    @Singleton
    public String defaultUserManifestLocation() {
        return getDefaultUserManifestLocation();
    }

    @Named(DEFAULT_MANIFEST_LOCATION)
    @Provides
    @Singleton
    public String defaultManifestLocation(CommandLine commandLine) {
        if (commandLine.hasOption(CONFIG)) {
            try {
                File file = new File(commandLine.getOptionValue(CONFIG)).getParentFile().getCanonicalFile();
                file.mkdirs();
                return file.getAbsolutePath();
            } catch (IOException exc) {
                log.warn("Failed to resolve default manifest location from config location {}",
                        commandLine.getOptionValue(CONFIG));
            }
        }

        if (!SystemUtils.IS_OS_WINDOWS) {
            File systemFile = new File(DEFAULT_LOCAL_PATH);
            if (!systemFile.exists()) {
                systemFile.mkdirs();
            }
            if (Files.isWritable(systemFile.toPath())) {
                return DEFAULT_LOCAL_PATH;
            }
        }
        return defaultUserManifestLocation();
    }

    @Provides
    @Singleton
    public WebServer webServer() {
        return WebServer.getInstance();
    }

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

    @Provides
    @Singleton
    public BackupConfiguration backupConfiguration(@Named(CONFIG_DATA) String configData)
            throws IOException {
        return BACKUP_CONFIGURATION_READER.readValue(configData);
    }

    @Provides
    @Singleton
    @Named(SOURCE_CONFIG_LOCATION)
    public String sourceConfigLocation(@Named(MANIFEST_LOCATION) String manifestLocation, @Named(ADDITIONAL_SOURCE) String source) {
        return Paths.get(manifestLocation, "sources", source, "config.json").toString();
    }

    @Provides
    @Singleton
    @Named(SOURCE_CONFIG)
    public BackupConfiguration backupConfiguration(BackupConfiguration configuration,
                                                   @Named(ADDITIONAL_SOURCE) String additionalSource,
                                                   @Named(SOURCE_CONFIG_LOCATION) String configLocation) throws IOException {
        if (!Strings.isNullOrEmpty(additionalSource)) {
            BackupConfiguration sourceConfig
                    = BACKUP_CONFIGURATION_READER.readValue(new File(configLocation));
            BackupDestination destination = configuration.getAdditionalSources().get(additionalSource);
            BackupDestination manifestDestination = sourceConfig.getDestinations().get(sourceConfig.getManifest().getDestination());

            // So this is a bit tricky. If the configuration for the manifest matches the URI and type we have then
            // replace the destination with what is defined in the backup configuration. Otherwise, add a new destination
            // with a generated name and make that the manifest location.
            if (!Objects.equals(destination.getEndpointUri(), manifestDestination.getEndpointUri())
                    || !Objects.equals(destination.getType(), manifestDestination.getType())) {
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
        return configuration;
    }


    @Provides
    @Singleton
    @Named(PRIVATE_KEY_SEED)
    public String privateKeySeed(CommandLine commandLine) {
        if (!Strings.isNullOrEmpty(this.passphrase)) {
            return passphrase;
        }
        if (commandLine.hasOption(PRIVATE_KEY_SEED))
            return commandLine.getOptionValue(PRIVATE_KEY_SEED);
        String seed = System.getenv("UNDERSCORE_BACKUP_SEED");
        if (Strings.isNullOrEmpty(seed))
            return "";
        return seed;
    }

    @Provides
    @Singleton
    @Named(PUBLIC_KEY_DATA)
    public String publicKeyData(CommandLine commandLine) {
        if (commandLine.hasOption(PUBLIC_KEY_DATA))
            return commandLine.getOptionValue(PUBLIC_KEY_DATA);
        return "";
    }

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
                try (FileOutputStream stream = new FileOutputStream(systemLogFile)) {
                }
                return DEFAULT_LOG_PATH;
            } catch (IOException exc) {
                // We don't have access default to user log file.
            }
        }

        return new File(getDefaultUserManifestLocation(), "underscorebackup.log").getAbsolutePath();
    }

    @Provides
    @Singleton
    @Named(KEY_FILE_NAME)
    public String getKeyFileName(CommandLine commandLine,
                                 @Named(PRIVATE_KEY_SEED) String privateKeySeed,
                                 @Named(MANIFEST_LOCATION) String manifestLocation,
                                 @Named(ADDITIONAL_SOURCE) String source)
            throws ParseException {
        String keyFile;
        if (!Strings.isNullOrEmpty(source)) {
            keyFile = Paths.get(manifestLocation, "sources", source, "key").toString();
        } else {
            keyFile = commandLine.getOptionValue(KEY);
        }
        if (Strings.isNullOrEmpty(keyFile)) {
            if (!Strings.isNullOrEmpty(privateKeySeed))
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

    @Provides
    @Singleton
    public StateLogger stateLogger(@Named(DEBUG) boolean debug) {
        return new StateLogger(debug);
    }

    @Provides
    @Singleton
    public ActivityAppender activityAppender() {
        return ActivityAppender.createAppender("Activity", null, null);
    }
}
