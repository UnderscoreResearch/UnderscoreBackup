package com.underscoreresearch.backup.cli.web.service;

import static com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl.sendApiFailureOn;
import static com.underscoreresearch.backup.utils.SerializationUtils.ENCRYPTION_KEY_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.takes.Request;
import org.takes.Response;
import org.takes.rq.RqPrint;

import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.base.Strings;
import com.underscoreresearch.backup.cli.web.ExclusiveImplementation;
import com.underscoreresearch.backup.cli.web.JsonWrap;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.service.api.model.SecretRequest;

public class CreateSecretPut extends JsonWrap {
    private static final ObjectReader READER = MAPPER.readerFor(CreateSecretPutRequest.class);

    public CreateSecretPut() {
        super(new Implementation());
    }

    public static EncryptionKey encryptionKey() {
        try {
            return InstanceFactory.getInstance(EncryptionKey.class);
        } catch (Exception e) {
            return null;
        }
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
            String config = new RqPrint(req).printBody();
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

                EncryptionKey encryptionKey = InstanceFactory.getInstance(EncryptionKey.class);
                EncryptionKey secretKey = EncryptionKey.changeEncryptionPassword(request.getPassword(),
                        request.getEmail(), encryptionKey);
                secretKey.setPublicKey(null);
                secretKey.setEncryptedAdditionalKeys(null);
                String secret = ENCRYPTION_KEY_WRITER.writeValueAsString(secretKey);
                String emailHash = Hash.hash64(request.getEmail().getBytes(StandardCharsets.UTF_8));

                serviceManager.call(request.getRegion(), (api) -> api.createSecret(serviceManager.getSourceId(),
                        new SecretRequest().secret(secret).emailHash(emailHash)));

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
