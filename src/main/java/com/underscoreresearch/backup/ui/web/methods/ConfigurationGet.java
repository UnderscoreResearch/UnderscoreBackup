package com.underscoreresearch.backup.ui.web.methods;

import com.underscoreresearch.backup.ui.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.ui.web.BaseImplementation;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;

import java.util.Map;

import static com.underscoreresearch.backup.ui.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.io.implementation.UnderscoreBackupProvider.UB_TYPE;
import static com.underscoreresearch.backup.io.implementation.UnderscoreBackupProvider.getRegion;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_WRITER;

/**
 * Web endpoint for retrieving the current backup configuration.
 * This class handles requests to get the current configuration of the backup system.
 */
@Slf4j
public class ConfigurationGet extends BaseWrap {

    /**
     * Creates a new ConfigurationGet instance.
     */
    public ConfigurationGet() {
        super(new Implementation());
    }

    /**
     * Implementation class that handles configuration retrieval requests.
     */
    private static class Implementation extends BaseImplementation {
        /**
         * Processes a request to get the current configuration.
         * Returns the configuration as a JSON response.
         *
         * @param req The HTTP request
         * @return The HTTP response containing the configuration
         * @throws Exception If an error occurs during processing
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            try {
                if (InstanceFactory.hasConfiguration(false)) {
                    BackupConfiguration config = InstanceFactory.getInstance(CommandLineModule.SOURCE_CONFIG,
                            BackupConfiguration.class);
                    if (InstanceFactory.getAdditionalSource() != null && config.getDestinations() != null) {
                        config = BACKUP_CONFIGURATION_READER.readValue(BACKUP_CONFIGURATION_WRITER
                                .writeValueAsString(config));
                        for (Map.Entry<String, BackupDestination> entry : config.getDestinations().entrySet()) {
                            if (UB_TYPE.equals(entry.getValue().getType()))
                                entry.getValue().setEndpointUri(getRegion(entry.getValue().getEndpointUri()));
                        }
                    }
                    return encryptResponse(req, BACKUP_CONFIGURATION_WRITER.writeValueAsString(config));
                } else if (InstanceFactory.getAdditionalSource() != null) {
                    log.warn("Have a source but an invalid configuration \"{}\", bailing",
                            InstanceFactory.getAdditionalSource());
                    InstanceFactory.reloadConfiguration(InteractiveCommand::startBackupIfAvailable);
                    return actualAct(req);
                }
            } catch (Exception exc) {
                log.warn("Failed to read existing config", exc);
            }
            return messageJson(404, "No valid config available");
        }
    }
}
