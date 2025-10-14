package com.underscoreresearch.backup.ui.web.methods;

import com.underscoreresearch.backup.ui.commands.GenerateKeyCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.takes.Request;
import org.takes.Response;

import static com.underscoreresearch.backup.ui.web.PrivateKeyRequest.decodePrivateKeyRequest;

/**
 * Web endpoint for generating a new encryption key.
 * This class handles requests to create a new encryption key for the backup system.
 */
@Slf4j
public class GenerateKeyPut extends BaseWrap {

    /**
     * Creates a new GenerateKeyPut instance.
     */
    public GenerateKeyPut() {
        super(new Implementation());
    }

    /**
     * Implementation class that handles key generation requests.
     */
    private static class Implementation extends ExclusiveImplementation {
        /**
         * Processes a request to generate a new encryption key.
         * Checks if a key already exists, and if not, generates a new one.
         *
         * @param req The HTTP request
         * @return The HTTP response
         * @throws Exception If an error occurs during processing
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            String password = decodePrivateKeyRequest(req);
            try {
                // Need to check that key doesn't already exist before doing this.
                try {
                    InstanceFactory.getInstance(EncryptionIdentity.class);
                    return messageJson(400, "Already have encryption key generated");
                } catch (Exception ignored) {
                }
                GenerateKeyCommand.generateAndSaveNewKey(InstanceFactory.getInstance(CommandLine.class),
                        password);
                InstanceFactory.reloadConfiguration(null);
                return messageJson(200, "Created new key configuration");
            } catch (Exception exc) {
                log.warn("Failed to generate key", exc);
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
            return "Generating new key";
        }
    }
}
