package com.underscoreresearch.backup.cli.web.service;

import static com.underscoreresearch.backup.cli.web.PsAuthedContent.decodeRequestBody;
import static com.underscoreresearch.backup.configuration.EncryptionModule.ROOT_KEY;
import static com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl.sendApiFailureOn;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.takes.Request;
import org.takes.Response;

import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.base.Strings;
import com.underscoreresearch.backup.cli.web.BaseWrap;
import com.underscoreresearch.backup.cli.web.ExclusiveImplementation;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.service.api.model.SecretRequest;

public class CreateSecretPut extends BaseWrap {
    private static final ObjectReader READER = MAPPER.readerFor(CreateSecretPutRequest.class);

    public CreateSecretPut() {
        super(new Implementation());
    }

    public static EncryptionIdentity encryptionIdentity() {
        try {
            return InstanceFactory.getInstance(ROOT_KEY, EncryptionIdentity.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static Response createSecret(ServiceManager serviceManager,
                                        EncryptionIdentity encryptionIdentity,
                                        String region, String password, String email) throws IOException {
        EncryptionIdentity secretKey;
        try {
            secretKey = encryptionIdentity.changeEncryptionPassword(password,
                    email, false);
        } catch (GeneralSecurityException exc) {
            return messageJson(403, "Invalid password");
        }
        String secret;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            secretKey.writeKey(EncryptionIdentity.KeyFormat.SERVICE, output);
            secret = output.toString(StandardCharsets.UTF_8);
        }
        String emailHash = Hash.hash64(email.getBytes(StandardCharsets.UTF_8));

        serviceManager.call(region, (api) -> api.createSecret(serviceManager.getSourceId(),
                new SecretRequest().secret(secret).emailHash(emailHash)));
        return null;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateSecretPutRequest {
        private String email;
        private String region;
        private String password;
    }

    private static class Implementation extends ExclusiveImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            String config = decodeRequestBody(req);
            CreateSecretPutRequest request = READER.readValue(config);
            try {
                ServiceManager serviceManager = InstanceFactory.getInstance(ServiceManager.class);
                if (Strings.isNullOrEmpty(serviceManager.getSourceId())) {
                    return messageJson(400, "No source selected");
                }

                if (Strings.isNullOrEmpty(request.getRegion())
                        || Strings.isNullOrEmpty(request.getEmail())
                        || Strings.isNullOrEmpty(request.getPassword())) {
                    return messageJson(400, "Missing required parameter");
                }

                Response Invalid_password = createSecret(serviceManager,
                        InstanceFactory.getInstance(EncryptionIdentity.class),
                        request.getRegion(), request.getPassword(), request.getEmail());
                if (Invalid_password != null) return Invalid_password;

                return messageJson(200, "Saved secret");
            } catch (IOException exc) {
                return sendApiFailureOn(exc);
            }
        }

        @Override
        protected String getBusyMessage() {
            return "Storing private key recovery information";
        }
    }
}
