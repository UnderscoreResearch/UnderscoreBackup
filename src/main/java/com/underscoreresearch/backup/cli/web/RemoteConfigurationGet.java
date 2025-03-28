package com.underscoreresearch.backup.cli.web;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.IOProviderUtil;
import com.underscoreresearch.backup.model.BackupConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;

import java.nio.charset.StandardCharsets;

import static com.underscoreresearch.backup.cli.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.manifest.implementation.ManifestManagerImpl.CONFIGURATION_FILENAME;

@Slf4j
public class RemoteConfigurationGet extends BaseWrap {

    public RemoteConfigurationGet() {
        super(new Implementation());
    }

    private static class Implementation extends ExclusiveImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            try {
                BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);
                IOProvider provider = IOProviderFactory.getProvider(configuration.getDestinations()
                        .get(configuration.getManifest().getDestination()));
                try {
                    return encryptResponse(req, new String(IOProviderUtil.download(provider, CONFIGURATION_FILENAME), StandardCharsets.UTF_8));
                } catch (Exception exc) {
                    return messageJson(400, "Couldn't fetch remote configuration");
                }
            } catch (Exception exc) {
                log.error("Failed to read existing config", exc);
            }
            return messageJson(404, "No valid config available");
        }

        @Override
        protected String getBusyMessage() {
            return "Downloading remote configuration";
        }
    }
}
