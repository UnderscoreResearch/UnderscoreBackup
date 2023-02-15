package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.ConfigurationPost.updateConfiguration;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_WRITER;

import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupConfiguration;

@Slf4j
public class BackupPauseGet extends JsonWrap {
    public BackupPauseGet() {
        super(new Implementation());
    }

    private static class Implementation extends BaseImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            String config = BACKUP_CONFIGURATION_WRITER
                    .writeValueAsString(InstanceFactory.getInstance(BackupConfiguration.class));
            try {
                updateConfiguration(config, true, false);
                InstanceFactory.reloadConfiguration(null);
                return messageJson(200, "Updated configuration");
            } catch (Exception exc) {
                return messageJson(400, exc.getMessage());
            }
        }
    }
}
