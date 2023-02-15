package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_WRITER;

import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;
import org.takes.rs.RsText;

import com.underscoreresearch.backup.cli.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupConfiguration;

@Slf4j
public class ConfigurationGet extends JsonWrap {

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
                    return new RsText(BACKUP_CONFIGURATION_WRITER.writeValueAsString(config));
                } else if (InstanceFactory.getAdditionalSource() != null) {
                    log.warn("Have a source but an invalid configuration {}, bailing",
                            InstanceFactory.getAdditionalSource());
                    InstanceFactory.reloadConfiguration(() -> InteractiveCommand.startBackupIfAvailable());
                    return actualAct(req);
                }
            } catch (Exception exc) {
                log.warn("Failed to read existing config", exc);
            }
            return messageJson(404, "No valid config available");
        }
    }
}
