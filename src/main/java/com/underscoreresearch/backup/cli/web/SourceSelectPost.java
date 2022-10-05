package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.commands.DownloadConfigCommand.storeKeyData;
import static com.underscoreresearch.backup.cli.commands.RebuildRepositoryCommand.downloadRemoteConfiguration;
import static com.underscoreresearch.backup.cli.web.ConfigurationPost.validateDestinations;
import static com.underscoreresearch.backup.cli.web.PrivateKeyRequest.decodePrivateKeyRequest;
import static com.underscoreresearch.backup.configuration.CommandLineModule.KEY_FILE_NAME;
import static com.underscoreresearch.backup.configuration.CommandLineModule.SOURCE_CONFIG;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.ParseException;
import org.takes.HttpException;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.misc.Href;
import org.takes.rq.RqHref;

import com.google.common.base.Strings;
import com.google.inject.ProvisionException;
import com.underscoreresearch.backup.cli.commands.RebuildRepositoryCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.PublicKeyEncrypion;
import com.underscoreresearch.backup.model.BackupConfiguration;

@Slf4j
public class SourceSelectPost extends JsonWrap {
    public SourceSelectPost(String base) {
        super(new Implementation(base));
    }

    private static class Implementation implements Take {
        private final String base;

        private Implementation(String base) {
            this.base = base + "/api/sources/";
        }

        @Override
        public Response act(Request req) throws Exception {
            Href href = new RqHref.Base(req).href();
            String source = URLDecoder.decode(href.path().substring(base.length()), StandardCharsets.UTF_8);
            if ("-".equals(source)) {
                source = null;
            }
            BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);

            String password;
            if (Strings.isNullOrEmpty(source)) {
                InstanceFactory.reloadConfiguration(null, null);
                try {
                    password = decodePrivateKeyRequest(req);
                } catch (HttpException exc) {
                    password = null;
                }
                if (password != null && !PrivateKeyRequest.validatePassphrase(password)) {
                    return messageJson(403, "Invalid passphrase provided");
                }
            } else {
                if (configuration.getAdditionalSources() == null || configuration.getAdditionalSources().get(source) == null) {
                    return messageJson(404, "Source not found");
                }

                password = decodePrivateKeyRequest(req);

                InstanceFactory.reloadConfiguration(password, source);
                try {
                    if (new File(InstanceFactory.getInstance(KEY_FILE_NAME)).exists()) {
                        InstanceFactory.getInstance(PublicKeyEncrypion.class);
                    } else {
                        throw new IllegalArgumentException();
                    }
                } catch (ProvisionException | IllegalArgumentException exc) {
                    try {
                        storeKeyData(password, source);
                        InstanceFactory.reloadConfiguration(password, source);
                    } catch (ParseException | IOException exc2) {
                        InstanceFactory.reloadConfiguration(null, null);
                        return messageJson(403, "Invalid passphrase provided");
                    }
                }

                BackupConfiguration sourceConfig;
                try {
                    sourceConfig = InstanceFactory.getInstance(SOURCE_CONFIG, BackupConfiguration.class);
                    InstanceFactory.reloadConfiguration(password, source,
                            () -> RebuildRepositoryCommand.rebuildFromLog(true));
                } catch (Exception exc) {
                    try {
                        String config = downloadRemoteConfiguration(source);
                        ConfigurationPost.updateSourceConfiguration(config, false);
                        InstanceFactory.reloadConfiguration(password, source,
                                () -> RebuildRepositoryCommand.rebuildFromLog(true));
                        sourceConfig = InstanceFactory.getInstance(SOURCE_CONFIG, BackupConfiguration.class);
                    } catch (Exception exc2) {
                        return messageJson(403, "Could not download source configuration");
                    }
                }

                try {
                    validateDestinations(sourceConfig);
                } catch (Exception exc) {
                    return messageJson(406, "Destinations in source are missing");
                }
            }

            return messageJson(200, "Ok");
        }
    }
}
