package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.commands.GenerateKeyCommand.getDefaultEncryptionFileName;
import static com.underscoreresearch.backup.cli.web.PsAuthedContent.decodeRequestBody;
import static com.underscoreresearch.backup.cli.web.RepairPost.executeAsyncOperation;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.File;
import java.io.IOException;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.util.Strings;
import org.takes.Request;
import org.takes.Response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectReader;
import com.underscoreresearch.backup.cli.commands.ChangePasswordCommand;
import com.underscoreresearch.backup.cli.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.ManifestManager;

@Slf4j
public class KeyChangePost extends BaseWrap {
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
        private boolean regeneratePrivateKey;

        @JsonCreator
        @Builder
        public KeyChangeRequest(@JsonProperty("newPassword") String newPassword,
                                @JsonProperty("password") String password,
                                @JsonProperty("regeneratePrivateKey") boolean regeneratePrivateKey) {
            super(password);

            this.newPassword = newPassword;
            this.regeneratePrivateKey = regeneratePrivateKey;
        }
    }

    private static class Implementation extends ExclusiveImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            KeyChangeRequest request = READER.readValue(decodeRequestBody(req));

            if (Strings.isEmpty(request.getPassword())) {
                return messageJson(400, "Missing password to change password");
            }

            if (Strings.isEmpty(request.getNewPassword())) {
                return messageJson(400, "Missing newPassword to change password");
            }

            if (!PrivateKeyRequest.validatePassword(request.getPassword())) {
                return messageJson(403, "Invalid password provided");
            }

            if (request.regeneratePrivateKey) {
                ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
                MetadataRepository metadataRepository = InstanceFactory.getInstance(MetadataRepository.class);
                executeAsyncOperation(() -> {
                            try {
                                File fileName = getDefaultEncryptionFileName(InstanceFactory.getInstance(CommandLine.class));

                                ChangePasswordCommand.generateNewPrivateKey(manifestManager,
                                        metadataRepository,
                                        fileName,
                                        request.getPassword(),
                                        request.getNewPassword());
                            } catch (IOException e) {
                                log.error("Failed to change password", e);
                            }
                        },
                        (thread, completed) -> {
                            try {
                                manifestManager.shutdown();
                                metadataRepository.close();
                            } catch (IOException e) {
                                log.error("Failed to close repository", e);
                            }
                        },
                        "PasswordChange");
            } else {
                ChangePasswordCommand.changePrivateKeyPassword(InstanceFactory.getInstance(CommandLine.class),
                        request.getPassword(),
                        request.getNewPassword());

                InstanceFactory.reloadConfiguration(
                        InteractiveCommand::startBackupIfAvailable);
            }

            return messageJson(200, "Ok");
        }

        @Override
        protected String getBusyMessage() {
            return "Changing password";
        }
    }
}
