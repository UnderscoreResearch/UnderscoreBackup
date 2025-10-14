package com.underscoreresearch.backup.ui.web.methods;

import com.google.common.base.Strings;
import com.underscoreresearch.backup.ui.ConfigurationValidator;
import com.underscoreresearch.backup.ui.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupShare;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import com.underscoreresearch.backup.ui.web.PsAuthedContent;
import org.takes.Request;
import org.takes.Response;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.ui.web.methods.ResetDelete.executeShielded;
import static com.underscoreresearch.backup.configuration.CommandLineModule.CONFIG_FILE_LOCATION;
import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.configuration.CommandLineModule.SOURCE_CONFIG_LOCATION;
import static com.underscoreresearch.backup.io.IOUtils.createDirectory;
import static com.underscoreresearch.backup.io.IOUtils.deleteContents;
import static com.underscoreresearch.backup.io.IOUtils.deleteFile;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_WRITER;

/**
 * Web endpoint for updating the backup configuration.
 * This class handles requests to update the configuration of the backup system.
 */
public class ConfigurationPost extends BaseWrap {

    private static BackupConfiguration cachedValidDestinationConfig;
    private static boolean cachedValidDestinationResult;
    private static String cachedServiceToken;

    /**
     * Creates a new ConfigurationPost instance.
     */
    public ConfigurationPost() {
        super(new Implementation());
    }

    /**
     * Removes data for a source from the filesystem.
     *
     * @param source The source to remove
     */
    public static void removeSourceData(String source) {
        File configParent = Paths.get(InstanceFactory.getInstance(MANIFEST_LOCATION), "sources", source).toFile();
        executeShielded(() -> deleteContents(configParent));
        deleteFile(configParent);
        File repositoryParent = Paths.get(InstanceFactory.getInstance(MANIFEST_LOCATION), "db", "sources", source).toFile();
        executeShielded(() -> deleteContents(repositoryParent));
        deleteFile(repositoryParent);
    }

    /**
     * Updates the backup configuration.
     * Validates the configuration and writes it to the configuration file.
     *
     * @param config The new configuration as a JSON string
     * @param clearInteractiveBackup Whether to clear the interactive backup setting
     * @param validateDestinations Whether to validate the destinations
     * @param initialSetup Whether this is the initial setup
     * @return The updated configuration
     * @throws IOException If there's an error updating the configuration
     */
    public static BackupConfiguration updateConfiguration(String config,
                                                          boolean clearInteractiveBackup,
                                                          boolean validateDestinations,
                                                          boolean initialSetup) throws IOException {
        BackupConfiguration configuration = BACKUP_CONFIGURATION_READER.readValue(config);
        if (clearInteractiveBackup || initialSetup) {
            if (clearInteractiveBackup)
                configuration.getManifest().setInteractiveBackup(null);
            if (initialSetup)
                configuration.getManifest().setInitialSetup(true);
            config = BACKUP_CONFIGURATION_WRITER.writeValueAsString(configuration);
        }
        ConfigurationValidator.validateConfiguration(configuration, false, false);
        if (validateDestinations) {
            validateDestinations(configuration);
        }

        writeConfig(config);

        return configuration;
    }

    /**
     * Writes the configuration to the configuration file.
     *
     * @param config The configuration as a JSON string
     * @throws IOException If there's an error writing the configuration
     */
    public static void writeConfig(String config) throws IOException {
        File file = new File(InstanceFactory.getInstance(CONFIG_FILE_LOCATION));
        boolean exists = file.exists();
        try (OutputStreamWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write(config);
        }

        if (!exists)
            IOUtils.setOwnerOnlyPermissions(file);
    }

    /**
     * Validates the destinations in a configuration.
     * Checks if the destinations are valid and can be accessed.
     *
     * @param configuration The configuration to validate
     * @throws IOException If there's an error validating the destinations
     */
    public static synchronized void validateDestinations(BackupConfiguration configuration) throws IOException {
        cachedValidDestinationConfig = configuration;
        cachedServiceToken = InstanceFactory.getInstance(ServiceManager.class).getToken();
        try {
            if (configuration.getDestinations() != null) {
                for (Map.Entry<String, BackupDestination> entry : configuration.getDestinations().entrySet()) {
                    IOProviderFactory.getProvider(entry.getValue()).checkCredentials(false);
                }
            }
            if (configuration.getAdditionalSources() != null) {
                for (Map.Entry<String, BackupDestination> entry : configuration.getAdditionalSources().entrySet()) {
                    IOProviderFactory.getProvider(entry.getValue()).checkCredentials(false);
                }
            }
            if (configuration.getShares() != null) {
                for (Map.Entry<String, BackupShare> entry : configuration.getShares().entrySet()) {
                    IOProviderFactory.getProvider(entry.getValue().getDestination()).checkCredentials(false);
                }
            }
            cachedValidDestinationResult = true;
        } catch (Exception exc) {
            cachedValidDestinationResult = false;
            throw exc;
        }
    }

    /**
     * Checks if the destinations in a configuration are valid.
     * Uses cached results if available.
     *
     * @param sourceConfig The configuration to check
     * @return True if the destinations are valid
     */
    public static synchronized boolean cachedValidDestinations(BackupConfiguration sourceConfig) {
        try {
            ServiceManager manager = InstanceFactory.getInstance(ServiceManager.class);
            if (Objects.equals(sourceConfig, cachedValidDestinationConfig) && Objects.equals(manager.getToken(), cachedServiceToken)) {
                return cachedValidDestinationResult;
            }
            validateDestinations(sourceConfig);
            return true;
        } catch (Exception exc) {
            return false;
        }
    }

    /**
     * Updates the configuration for a source.
     * Validates the configuration and writes it to the source configuration file.
     *
     * @param config The new configuration as a JSON string
     * @param validateDestinations Whether to validate the destinations
     * @throws IOException If there's an error updating the configuration
     */
    public static void updateSourceConfiguration(String config, boolean validateDestinations) throws IOException {
        BackupConfiguration mainConfig = InstanceFactory.getInstance(BackupConfiguration.class);
        String source = InstanceFactory.getAdditionalSource();
        BackupConfiguration configuration = BACKUP_CONFIGURATION_READER.readValue(config);

        if (mainConfig.getAdditionalSources() == null || mainConfig.getAdditionalSources().get(source) == null) {
            String sourceId = InstanceFactory.getAdditionalSource();
            int ind = sourceId.indexOf('.');
            String shareId;
            if (ind > 0) {
                shareId = sourceId.substring(ind + 1);
                sourceId = sourceId.substring(0, ind);
            } else {
                shareId = null;
            }

            final String finalSourceId = sourceId;
            final String finalShareId = shareId;

            configuration.setDestinations(configuration.getDestinations().entrySet().stream()
                    .map(entry -> Map.entry(entry.getKey(),
                            entry.getValue().sourceShareDestination(finalSourceId, finalShareId)))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }

        ConfigurationValidator.validateConfiguration(configuration, false, true);
        if (validateDestinations) {
            validateDestinations(configuration);
        }
        File configFile = new File(InstanceFactory.getInstance(SOURCE_CONFIG_LOCATION));
        createDirectory(configFile.getParentFile(), true);
        BACKUP_CONFIGURATION_WRITER.writeValue(configFile, configuration);
        IOUtils.setOwnerOnlyPermissions(configFile);
    }

    /**
     * Implementation class that handles configuration update requests.
     */
    private static class Implementation extends ExclusiveImplementation {
        /**
         * Processes a request to update the configuration.
         * Updates the configuration and reloads the system with the new configuration.
         *
         * @param req The HTTP request
         * @return The HTTP response
         * @throws Exception If an error occurs during processing
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            String config = PsAuthedContent.decodeRequestBody(req);
            try {
                if (Strings.isNullOrEmpty(InstanceFactory.getAdditionalSource())) {
                    BackupConfiguration currentConfig = null;
                    if (InstanceFactory.hasConfiguration(false))
                        currentConfig = InstanceFactory.getInstance(BackupConfiguration.class);
                    if (InstanceFactory.getAdditionalSource() != null)
                        InstanceFactory.reloadConfiguration(null);

                    BackupConfiguration newConfig = updateConfiguration(config, false, true, false);
                    if (currentConfig != null && currentConfig.getAdditionalSources() != null) {
                        HashSet<String> abandonedSources = new HashSet<>(currentConfig.getAdditionalSources().keySet());
                        if (newConfig.getAdditionalSources() != null) {
                            abandonedSources.removeAll(newConfig.getAdditionalSources().keySet());
                        }

                        abandonedSources.forEach(ConfigurationPost::removeSourceData);
                    }
                    InstanceFactory.reloadConfiguration(InteractiveCommand::startBackupIfAvailable);
                } else {
                    updateSourceConfiguration(config, true);
                    InstanceFactory.reloadConfigurationWithSource();
                }
                return messageJson(200, "Updated configuration");
            } catch (Exception exc) {
                return messageJson(400, exc.getMessage());
            }
        }

        /**
         * Gets the message to display when the system is busy.
         *
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Configuration is being updated";
        }
    }
}
