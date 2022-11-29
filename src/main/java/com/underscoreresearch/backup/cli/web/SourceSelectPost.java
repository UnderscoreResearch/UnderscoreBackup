package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.commands.DownloadConfigCommand.storeKeyData;
import static com.underscoreresearch.backup.cli.commands.RebuildRepositoryCommand.downloadRemoteConfiguration;
import static com.underscoreresearch.backup.cli.web.ConfigurationPost.validateDestinations;
import static com.underscoreresearch.backup.cli.web.PrivateKeyRequest.decodePrivateKeyRequest;
import static com.underscoreresearch.backup.configuration.CommandLineModule.SOURCE_CONFIG;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_WRITER;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.ParseException;
import org.takes.HttpException;
import org.takes.Request;
import org.takes.Response;
import org.takes.misc.Href;
import org.takes.rq.RqHref;

import com.google.common.base.Strings;
import com.google.inject.ProvisionException;
import com.underscoreresearch.backup.cli.commands.InteractiveCommand;
import com.underscoreresearch.backup.cli.commands.RebuildRepositoryCommand;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;

@Slf4j
public class SourceSelectPost extends JsonWrap {
    public SourceSelectPost(String base) {
        super(new Implementation(base));
    }

    private static class Implementation extends BaseImplementation {
        private final String base;

        private Implementation(String base) {
            this.base = base + "/api/sources/";
        }

        @Override
        public Response actualAct(Request req) throws Exception {
            Href href = new RqHref.Base(req).href();
            String source = URLDecoder.decode(href.path().substring(base.length()), StandardCharsets.UTF_8);
            if ("-".equals(source)) {
                source = null;
            }
            BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);

            String password;
            if (Strings.isNullOrEmpty(source)) {
                try {
                    password = decodePrivateKeyRequest(req);
                } catch (HttpException exc) {
                    password = null;
                }
                if (password != null) {
                    InstanceFactory.reloadConfiguration(null, null);
                    if (password != null && !PrivateKeyRequest.validatePassphrase(password)) {
                        return messageJson(403, "Invalid passphrase provided");
                    }
                }
                InstanceFactory.reloadConfiguration(null,
                        () -> InteractiveCommand.startBackupIfAvailable());
            } else {
                if (configuration.getAdditionalSources() == null || configuration.getAdditionalSources().get(source) == null) {
                    return messageJson(404, "Source not found");
                }

                password = decodePrivateKeyRequest(req);

                InstanceFactory.reloadConfiguration(source);
                try {
                    if (new File(CommandLineModule.getKeyFileName(source)).exists()) {
                        InstanceFactory.getInstance(EncryptionKey.class);
                    } else {
                        throw new IllegalArgumentException();
                    }
                } catch (ProvisionException | IllegalArgumentException exc) {
                    try {
                        storeKeyData(password, source);
                        InstanceFactory.reloadConfiguration(source);
                    } catch (ParseException | IOException exc2) {
                        InstanceFactory.reloadConfiguration(null, null);
                        return messageJson(403, "Invalid passphrase provided");
                    }
                }

                BackupConfiguration sourceConfig;
                String config;
                try {
                    config = downloadRemoteConfiguration(source, password);
                } catch (Exception exc) {
                    InstanceFactory.reloadConfiguration(null, null);
                    return messageJson(403, "Could not download source configuration");
                }
                try {
                    sourceConfig = InstanceFactory.getInstance(SOURCE_CONFIG, BackupConfiguration.class);

                    BackupConfiguration newConfig = BACKUP_CONFIGURATION_READER.readValue(config);
                    if (newConfig.getDestinations() != null) {
                        boolean anyFound = false;
                        for (Map.Entry<String, BackupDestination> entry : newConfig.getDestinations().entrySet()) {
                            if (!sourceConfig.getDestinations().containsKey(entry.getKey())) {
                                log.warn("Found new destination {} in share", entry.getKey());
                                sourceConfig.getDestinations().put(entry.getKey(), entry.getValue());
                                anyFound = true;
                            }
                        }
                        if (anyFound) {
                            ConfigurationPost.updateSourceConfiguration(BACKUP_CONFIGURATION_WRITER.writeValueAsString(sourceConfig),
                                    false);
                        }
                    }
                } catch (Exception exc) {
                    try {
                        ConfigurationPost.updateSourceConfiguration(config, false);
                        InstanceFactory.reloadConfiguration(source, null);
                        sourceConfig = InstanceFactory.getInstance(SOURCE_CONFIG, BackupConfiguration.class);
                    } catch (Exception exc2) {
                        InstanceFactory.reloadConfiguration(null, null);
                        return messageJson(403, "Could not download source configuration");
                    }
                }

                try {
                    validateDestinations(sourceConfig);
                } catch (Exception exc) {
                    return messageJson(406, "Destinations in source are missing credentials");
                }
                String rebuildPassword = password;
                InstanceFactory.reloadConfiguration(source,
                        () -> RebuildRepositoryCommand.rebuildFromLog(rebuildPassword, true));
            }

            return messageJson(200, "Ok");
        }
    }
}
