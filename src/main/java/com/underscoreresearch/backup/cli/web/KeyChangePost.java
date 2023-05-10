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
import com.underscoreresearch.backup.cli.commands.ChangePasswordCommand;
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
        private String newPassword;

        @JsonCreator
        @Builder
        public KeyChangeRequest(@JsonProperty("newPassword") String newPassword,
                                @JsonProperty("password") String password) {
            super(password);

            this.newPassword = newPassword;
        }
    }

    private static class Implementation extends ExclusiveImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            KeyChangeRequest request = READER.readValue(new RqPrint(req).printBody());

            if (Strings.isEmpty(request.getPassword())) {
                return messageJson(400, "Missing password to change password");
            }

            if (Strings.isEmpty(request.getNewPassword())) {
                return messageJson(400, "Missing newPassword to change password");
            }

            if (!PrivateKeyRequest.validatePassword(request.getPassword())) {
                return messageJson(403, "Invalid password provided");
            }

            ChangePasswordCommand.changePrivateKeyPassword(InstanceFactory.getInstance(CommandLine.class),
                    request.getPassword(),
                    request.getNewPassword());

            InstanceFactory.reloadConfiguration(
                    () -> InteractiveCommand.startBackupIfAvailable());

            return messageJson(200, "Ok");
        }

        @Override
        protected String getBusyMessage() {
            return "Changing password";
        }
    }
}
