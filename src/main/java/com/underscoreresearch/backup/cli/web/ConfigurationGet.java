package com.underscoreresearch.backup.cli.web;

import com.underscoreresearch.backup.cli.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;

import java.util.Map;

import static com.underscoreresearch.backup.cli.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.io.implementation.UnderscoreBackupProvider.UB_TYPE;
import static com.underscoreresearch.backup.io.implementation.UnderscoreBackupProvider.getRegion;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_WRITER;

@Slf4j
public class ConfigurationGet extends BaseWrap {

    public ConfigurationGet() {
        super(new Implementation());
    }

    private static class Implementation extends BaseImplementation {
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
