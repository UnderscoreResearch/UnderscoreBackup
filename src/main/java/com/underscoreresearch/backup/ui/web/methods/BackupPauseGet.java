package com.underscoreresearch.backup.ui.web.methods;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;

import static com.underscoreresearch.backup.ui.web.methods.ConfigurationPost.updateConfiguration;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_WRITER;

/**
 * Web endpoint for pausing backup operations.
 * This class handles requests to pause the backup process.
 */
@Slf4j
public class BackupPauseGet extends BaseWrap {
    /**
     * Creates a new BackupPauseGet instance.
     */
    public BackupPauseGet() {
        super(new Implementation());
    }

    /**
     * Implementation class that handles backup pause requests.
     */
    private static class Implementation extends ExclusiveImplementation {
        /**
         * Processes a request to pause backup operations.
         * Updates the configuration to pause the backup process.
         *
         * @param req The HTTP request
         * @return The HTTP response
         * @throws Exception If an error occurs during processing
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            String config = BACKUP_CONFIGURATION_WRITER
                    .writeValueAsString(InstanceFactory.getInstance(BackupConfiguration.class));
            try {
                updateConfiguration(config, true, false, false);
                InstanceFactory.reloadConfiguration(null);
                return messageJson(200, "Updated configuration");
            } catch (Exception exc) {
                return messageJson(400, exc.getMessage());
            }
        }

        /**
         * Gets the message to display when the system is busy.
         *
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Pausing backup";
        }
    }
}
