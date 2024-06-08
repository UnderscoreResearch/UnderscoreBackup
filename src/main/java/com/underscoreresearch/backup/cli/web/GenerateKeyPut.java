package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.PrivateKeyRequest.decodePrivateKeyRequest;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;
import org.takes.Request;
import org.takes.Response;

import com.underscoreresearch.backup.cli.commands.GenerateKeyCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;

@Slf4j
public class GenerateKeyPut extends BaseWrap {

    public GenerateKeyPut() {
        super(new Implementation());
    }

    private static class Implementation extends ExclusiveImplementation {
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

        @Override
        protected String getBusyMessage() {
            return "Generating new key";
        }
    }
}
