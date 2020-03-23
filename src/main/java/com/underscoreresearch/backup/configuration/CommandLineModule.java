package com.underscoreresearch.backup.configuration;

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
import com.underscoreresearch.backup.model.BackupConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;
import org.apache.commons.lang.SystemUtils;
import org.apache.logging.log4j.core.util.IOUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.time.Instant;

import static com.underscoreresearch.backup.configuration.EncryptionModule.DEFAULT_KEY_FILES;
import static com.underscoreresearch.backup.utils.LogUtil.formatTimestamp;

@Slf4j
public class CommandLineModule extends AbstractModule {
    public static final String NEED_PRIVATE_KEY = "NEED_PRIVATE_KEY";

    public static final String PRIVATE_KEY_SEED = "private-key-seed";
    public static final String KEY_FILE_NAME = "key-file-name";
    public static final String PUBLIC_KEY_DATA = "public-key-data";
    public static final String CONFIG_DATA = "config-data";
    public static final String KEY = "key";
    public static final String DEBUG = "debug";
    public static final String FORCE = "force";
    public static final String CONFIG = "config";
    public static final String HUMAN_READABLE = "human-redable";
    public static final String RECURSIVE = "recursive";
    public static final String OVER_WRITE = "over-write";
    public static final String TIMESTAMP = "timestamp";

    private static final String DEFAULT_CONFIG = "/etc/underscorebackup/config.json";
    private static final String DEFAULT_WINDOWS_CONFIG = "C:\\UnderscoreBackup\\config.json";

    private static final ObjectReader BACKUP_CONFIGURATION_READER
            = new ObjectMapper().readerFor(BackupConfiguration.class);

    private final String[] argv;

    public CommandLineModule(String[] argv) {
        this.argv = argv;
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
        options.addOption(null, PRIVATE_KEY_SEED, true, "Private key seed");
        options.addOption("h", HUMAN_READABLE, false, "Display human readable sizes");
        options.addOption("R", RECURSIVE, false, "Process restore or list operation recursively");
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
    @Named(NEED_PRIVATE_KEY)
    public boolean needPrivateKey(CommandLine commandLine) {
        if (commandLine.getArgList().size() > 0) {
            Class<Command> commandClass = Command.findCommandClass(commandLine.getArgList().get(0));
            if (commandClass != null) {
                return Command.needPrivateKey(commandClass);
            }
        }
        return false;
    }

    @Named(CONFIG_DATA)
    @Singleton
    @Provides
    public String backupConfiguration(CommandLine commandLine) throws IOException {
        String configFile;
        if (commandLine.hasOption(CONFIG)) {
            configFile = commandLine.getOptionValue(CONFIG);

            if (commandLine.hasOption(CONFIG_DATA)) {
                throw new IllegalArgumentException("Can't specify both config file and config data");
            }
        } else {
            if (commandLine.hasOption(CONFIG_DATA)) {
                return commandLine.getOptionValue(CONFIG_DATA);
            }
            if (SystemUtils.IS_OS_WINDOWS)
                configFile = DEFAULT_WINDOWS_CONFIG;
            else
                configFile = DEFAULT_CONFIG;
        }
        try (Reader reader = new FileReader(new File(configFile))) {
            return IOUtils.toString(reader);
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
    @Named(PRIVATE_KEY_SEED)
    public String privateKeySeed(CommandLine commandLine) {
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
    @Named(KEY_FILE_NAME)
    public String getKeyFileName(CommandLine commandLine, @Named(PRIVATE_KEY_SEED) String privateKeySeed)
            throws ParseException {
        String keyFile = commandLine.getOptionValue(KEY);
        if (Strings.isNullOrEmpty(keyFile)) {
            if (!Strings.isNullOrEmpty(privateKeySeed))
                return DEFAULT_KEY_FILES[0];

            for (String file : DEFAULT_KEY_FILES) {
                if (new File(file).isFile()) {
                    keyFile = file;
                }
            }
        }
        if (Strings.isNullOrEmpty(keyFile)) {
            throw new ParseException("Could not find a key file. Try using the --generate-key option");
        }
        return keyFile;
    }
}
