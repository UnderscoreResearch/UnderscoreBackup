package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.ResetDelete.deleteContents;
import static com.underscoreresearch.backup.cli.web.ResetDelete.executeShielded;
import static com.underscoreresearch.backup.configuration.CommandLineModule.CONFIG_FILE_LOCATION;
import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.configuration.CommandLineModule.SOURCE_CONFIG_LOCATION;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.SystemUtils;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rq.RqPrint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Strings;
import com.underscoreresearch.backup.cli.ConfigurationValidator;
import com.underscoreresearch.backup.cli.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;

@Slf4j
public class ConfigurationPost extends JsonWrap {
    private static final ObjectReader READER = new ObjectMapper()
            .readerFor(BackupConfiguration.class);
    private static final ObjectWriter WRITER = new ObjectMapper()
            .writerFor(BackupConfiguration.class);

    public ConfigurationPost() {
        super(new Implementation());
    }

    private static class Implementation extends BaseImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            String config = new RqPrint(req).printBody();
            try {
                if (Strings.isNullOrEmpty(InstanceFactory.getAdditionalSource())) {
                    BackupConfiguration currentConfig = null;
                    if (InstanceFactory.hasConfiguration(false))
                        currentConfig = InstanceFactory.getInstance(BackupConfiguration.class);
                    if (InstanceFactory.getAdditionalSource() != null)
                        InstanceFactory.reloadConfiguration(null, null);

                    BackupConfiguration newConfig = updateConfiguration(config, false, true);
                    if (currentConfig != null && currentConfig.getAdditionalSources() != null) {
                        HashSet<String> abandonedSources = new HashSet<>(currentConfig.getAdditionalSources().keySet());
                        if (newConfig.getAdditionalSources() != null) {
                            abandonedSources.removeAll(newConfig.getAdditionalSources().keySet());
                        }
                        abandonedSources.forEach(source -> removeSourceData(source));
                    }
                    InstanceFactory.reloadConfiguration(null, null,
                            () -> InteractiveCommand.startBackupIfAvailable());
                } else {
                    updateSourceConfiguration(config, true);
                    InstanceFactory.reloadConfiguration(null, InstanceFactory.getAdditionalSource());
                }
                return messageJson(200, "Updated configuration");
            } catch (Exception exc) {
                return messageJson(400, exc.getMessage());
            }
        }
    }

    public static void removeSourceData(String source) {
        File configParent = Paths.get(InstanceFactory.getInstance(MANIFEST_LOCATION), "sources", source).toFile();
        executeShielded(() -> deleteContents(configParent));
        configParent.delete();
        File repositoryParent = Paths.get(InstanceFactory.getInstance(MANIFEST_LOCATION), "db", "sources", source).toFile();
        executeShielded(() -> deleteContents(repositoryParent));
        repositoryParent.delete();
    }

    public static BackupConfiguration updateConfiguration(String config,
                                                          boolean clearInteractiveBackup,
                                                          boolean validateDestinations) throws IOException {
        BackupConfiguration configuration = READER.readValue(config);
        if (clearInteractiveBackup) {
            configuration.getManifest().setInteractiveBackup(null);
            config = WRITER.writeValueAsString(configuration);
        }
        ConfigurationValidator.validateConfiguration(configuration, false, false);
        if (validateDestinations) {
            validateDestinations(configuration);
        }
        File file = new File(InstanceFactory.getInstance(CONFIG_FILE_LOCATION));
        boolean exists = file.exists();
        try (OutputStreamWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write(config);
        }

        if (!exists)
            setReadOnlyFilePermissions(file);
        return configuration;
    }

    public static void validateDestinations(BackupConfiguration configuration) throws IOException {
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
    }

    public static void updateSourceConfiguration(String config, boolean validateDestinations) throws IOException {
        BackupConfiguration configuration = READER.readValue(config);
        ConfigurationValidator.validateConfiguration(configuration, false, true);
        if (validateDestinations) {
            validateDestinations(configuration);
        }
        File configFile = new File(InstanceFactory.getInstance(SOURCE_CONFIG_LOCATION));
        configFile.getParentFile().mkdirs();
        WRITER.writeValue(configFile, configuration);
        setReadOnlyFilePermissions(configFile);
    }

    public static void setReadOnlyFilePermissions(File file) throws IOException {
        if (!SystemUtils.IS_OS_WINDOWS) {
            HashSet<PosixFilePermission> set = new HashSet<PosixFilePermission>();

            set.add(PosixFilePermission.OWNER_READ);
            set.add(PosixFilePermission.OWNER_WRITE);

            Files.setPosixFilePermissions(file.toPath(), set);
        }
    }
}
