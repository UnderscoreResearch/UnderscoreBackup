package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.util.Strings;
import org.takes.Request;
import org.takes.Response;
import org.takes.rq.RqPrint;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectReader;
import com.underscoreresearch.backup.cli.commands.ChangePassphraseCommand;
import com.underscoreresearch.backup.cli.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;

@Slf4j
public class KeyChangePost extends JsonWrap {
    private static final ObjectReader READER = MAPPER
            .readerFor(KeyChangeRequest.class);

    public KeyChangePost() {
        super(new Implementation());
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class KeyChangeRequest extends PrivateKeyRequest {
        private String newPassphrase;

        @JsonCreator
        @Builder
        public KeyChangeRequest(@JsonProperty("newPassphrase") String newPassphrase,
                                @JsonProperty("passphrase") String passphrase) {
            super(passphrase);

            this.newPassphrase = newPassphrase;
        }
    }

    private static class Implementation extends BaseImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            KeyChangeRequest request = READER.readValue(new RqPrint(req).printBody());

            if (Strings.isEmpty(request.getPassphrase())) {
                return messageJson(400, "Missing passphrase to change password");
            }

            if (Strings.isEmpty(request.getNewPassphrase())) {
                return messageJson(400, "Missing newPassphrase to change password");
            }

            if (!PrivateKeyRequest.validatePassphrase(request.getPassphrase())) {
                return messageJson(403, "Invalid passphrase provided");
            }

            ChangePassphraseCommand.generateAndSaveNewKey(InstanceFactory.getInstance(CommandLine.class),
                    request.getPassphrase(),
                    request.getNewPassphrase());

            InstanceFactory.reloadConfiguration(null,
                    () -> InteractiveCommand.startBackupIfAvailable());

            return messageJson(200, "Ok");
        }
    }
}
