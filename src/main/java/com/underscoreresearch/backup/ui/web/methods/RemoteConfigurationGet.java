package com.underscoreresearch.backup.ui.web.methods;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.IOProviderUtil;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;

import java.nio.charset.StandardCharsets;

import static com.underscoreresearch.backup.ui.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.manifest.implementation.ManifestManagerImpl.CONFIGURATION_FILENAME;

/**
 * Web endpoint for retrieving remote backup configuration.
 * This class handles requests to download the configuration file from the backup destination.
 */
@Slf4j
public class RemoteConfigurationGet extends BaseWrap {

    /**
     * Creates a new RemoteConfigurationGet instance.
     */
    public RemoteConfigurationGet() {
        super(new Implementation());
    }

    /**
     * Implementation class that handles remote configuration retrieval requests.
     */
    private static class Implementation extends ExclusiveImplementation {
        /**
         * Processes a request to get the remote configuration.
         * Downloads the configuration file from the backup destination.
         *
         * @param req The HTTP request
         * @return The HTTP response containing the configuration file
         * @throws Exception If an error occurs during processing
         */
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

        /**
         * Gets the message to display when the system is busy.
         *
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Downloading remote configuration";
        }
    }
}
