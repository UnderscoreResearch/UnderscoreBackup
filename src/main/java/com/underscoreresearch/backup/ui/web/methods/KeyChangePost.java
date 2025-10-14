package com.underscoreresearch.backup.ui.web.methods;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectReader;
import com.underscoreresearch.backup.ui.commands.ChangePasswordCommand;
import com.underscoreresearch.backup.ui.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import com.underscoreresearch.backup.ui.web.PrivateKeyRequest;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.util.Strings;
import org.takes.Request;
import org.takes.Response;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

import static com.underscoreresearch.backup.ui.commands.ChangePasswordCommand.removeSecret;
import static com.underscoreresearch.backup.ui.commands.GenerateKeyCommand.getDefaultEncryptionFileName;
import static com.underscoreresearch.backup.ui.web.PsAuthedContent.decodeRequestBody;
import static com.underscoreresearch.backup.ui.web.methods.RepairPost.executeAsyncOperation;
import static com.underscoreresearch.backup.ui.web.methods.service.CreateSecretPut.createSecret;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Web endpoint for changing the encryption key password.
 * This class handles requests to change the password for the encryption key,
 * with options to regenerate the private key and save a recovery secret.
 */
@Slf4j
public class KeyChangePost extends BaseWrap {
    private static final ObjectReader READER = MAPPER
            .readerFor(KeyChangeRequest.class);

    /**
     * Creates a new KeyChangePost instance.
     */
    public KeyChangePost() {
        super(new Implementation());
    }

    /**
     * Request class for changing the encryption key password.
     * Contains the current password, new password, and options for key regeneration and secret recovery.
     */
    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class KeyChangeRequest extends PrivateKeyRequest {
        private String newPassword;
        private boolean regeneratePrivateKey;
        private boolean saveSecret;
        private String secretRegion;
        private String email;

        /**
         * Creates a new KeyChangeRequest with the specified parameters.
         *
         * @param newPassword The new password for the encryption key
         * @param password The current password for the encryption key
         * @param regeneratePrivateKey Whether to regenerate the private key
         * @param saveSecret Whether to save a recovery secret
         * @param secretRegion The region for the recovery secret
         * @param email The email address for recovery notifications
         */
        @JsonCreator
        @Builder
        public KeyChangeRequest(@JsonProperty("newPassword") String newPassword,
                                @JsonProperty("password") String password,
                                @JsonProperty("regeneratePrivateKey") boolean regeneratePrivateKey,
                                @JsonProperty("saveSecret") boolean saveSecret,
                                @JsonProperty("secretRegion") String secretRegion,
                                @JsonProperty("email") String email) {
            super(password);

            this.newPassword = newPassword;
            this.regeneratePrivateKey = regeneratePrivateKey;
            this.saveSecret = saveSecret;
            this.secretRegion = secretRegion;
            this.email = email;
        }
    }

    /**
     * Implementation class that handles key password change requests.
     */
    private static class Implementation extends ExclusiveImplementation {
        /**
         * Processes a request to change the encryption key password.
         * Validates the request and initiates the password change process.
         *
         * @param req The HTTP request
         * @return The HTTP response
         * @throws Exception If an error occurs during processing
         */
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
                InstanceFactory.reloadConfiguration(InstanceFactory.getAdditionalSource(),
                        InstanceFactory.getAdditionalSourceName(), null);

                ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
                MetadataRepository metadataRepository = InstanceFactory.getInstance(MetadataRepository.class);
                executeAsyncOperation(() -> {
                            EncryptionIdentity newIdentity = null;
                            try {
                                File fileName = getDefaultEncryptionFileName(InstanceFactory.getInstance(CommandLine.class));

                                newIdentity = ChangePasswordCommand.generateNewPrivateKey(manifestManager,
                                        metadataRepository,
                                        fileName,
                                        request.getPassword(),
                                        request.getNewPassword());
                            } catch (IOException | GeneralSecurityException e) {
                                log.error("Failed to change password", e);
                            }

                            if (request.isSaveSecret() && newIdentity != null) {
                                try {
                                    ServiceManager serviceManager = InstanceFactory.getInstance(ServiceManager.class);
                                    Response response = createSecret(serviceManager,
                                            newIdentity,
                                            request.getSecretRegion(),
                                            request.getNewPassword(),
                                            request.getEmail());
                                    if (response != null) {
                                        log.error("Failed to update private key recovery");
                                    }
                                } catch (IOException e) {
                                    log.error("Failed to update private key recovery", e);
                                }
                            } else {
                                ServiceManager serviceManager = InstanceFactory.getInstance(ServiceManager.class);
                                removeSecret(serviceManager);
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
                        ChangePasswordCommand.RE_KEY_OPERATION_NAME,
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

        /**
         * Gets the message to display when the system is busy.
         *
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Changing password";
        }
    }
}
