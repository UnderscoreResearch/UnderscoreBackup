package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.PrivateKeyRequest.decodePrivateKeyRequest;

import org.apache.commons.cli.CommandLine;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;

import com.underscoreresearch.backup.cli.commands.GenerateKeyCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;

public class GenerateKeyPut extends JsonWrap {

    public GenerateKeyPut() {
        super(new Implementation());
    }

    private static class Implementation extends BaseImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            String passphrase = decodePrivateKeyRequest(req);
            try {
                GenerateKeyCommand.generateAndSaveNewKey(InstanceFactory.getInstance(CommandLine.class),
                        passphrase);
                InstanceFactory.reloadConfiguration(null, null);
                return messageJson(200, "Created new key configuration");
            } catch (Exception exc) {
                return messageJson(400, exc.getMessage());
            }
        }
    }
}
