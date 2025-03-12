package com.underscoreresearch.backup.cli.web;

import com.google.common.base.Strings;
import com.underscoreresearch.backup.cli.ConfigurationValidator;
import com.underscoreresearch.backup.cli.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupShare;
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

import static com.underscoreresearch.backup.cli.web.ResetDelete.executeShielded;
import static com.underscoreresearch.backup.configuration.CommandLineModule.CONFIG_FILE_LOCATION;
import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.configuration.CommandLineModule.SOURCE_CONFIG_LOCATION;
import static com.underscoreresearch.backup.io.IOUtils.createDirectory;
import static com.underscoreresearch.backup.io.IOUtils.deleteContents;
import static com.underscoreresearch.backup.io.IOUtils.deleteFile;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_WRITER;

public class ConfigurationPost extends BaseWrap {

    private static BackupConfiguration cachedValidDestinationConfig;
    private static boolean cachedValidDestinationResult;
    private static String cachedServiceToken;

    public ConfigurationPost() {
        super(new Implementation());
    }

    public static void removeSourceData(String source) {
        File configParent = Paths.get(InstanceFactory.getInstance(MANIFEST_LOCATION), "sources", source).toFile();
        executeShielded(() -> deleteContents(configParent));
        deleteFile(configParent);
        File repositoryParent = Paths.get(InstanceFactory.getInstance(MANIFEST_LOCATION), "db", "sources", source).toFile();
        executeShielded(() -> deleteContents(repositoryParent));
        deleteFile(repositoryParent);
    }

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
     * This method will check if the configuration destinations are valid. If not exceptions will be thrown indicating
     * the error.
     *
     * @param configuration Configuration.
     * @throws IOException Errors found.
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
     * Check if a destination is valid. Will just return true or false and not throw any exceptions. This
     * method will also cache the results
     *
     * @param sourceConfig Configuration
     * @return True if the destinations are valid.
     */
    public static synchronized boolean isValidatesDestinations(BackupConfiguration sourceConfig) {
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

    private static class Implementation extends ExclusiveImplementation {
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

        @Override
        protected String getBusyMessage() {
            return "Configuration is being updated";
        }
    }
}
