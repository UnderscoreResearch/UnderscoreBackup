package com.underscoreresearch.backup.cli.web.service;

import static com.underscoreresearch.backup.cli.web.service.SourcesPut.restoreFromSource;
import static com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl.CLIENT_ID;
import static com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl.sendApiFailureOn;
import static com.underscoreresearch.backup.utils.SerializationUtils.ENCRYPTION_KEY_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;
import org.takes.rq.RqPrint;
import org.takes.rs.RsText;
import org.takes.rs.RsWithStatus;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Strings;
import com.underscoreresearch.backup.cli.web.ExclusiveImplementation;
import com.underscoreresearch.backup.cli.web.JsonWrap;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.service.api.model.GetSecretRequest;
import com.underscoreresearch.backup.service.api.model.GetSecretResponse;
import com.underscoreresearch.backup.service.api.model.SourceResponse;

@Slf4j
public class GetSecretPost extends JsonWrap {
    private static final ObjectReader READER = MAPPER.readerFor(GetSecretPostRequest.class);
    private static final ObjectWriter WRITER = MAPPER.writerFor(GetSecretPostResponse.class);

    public GetSecretPost() {
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
    public static class GetSecretPostRequest {
        private String region;
        private String email;
        private String codeVerifier;
        private String code;
        private String sourceId;
        private String password;
    }

    @Data
    @AllArgsConstructor
    public static class GetSecretPostResponse {
        private boolean available;
        private boolean installed;
    }

    private static class Implementation extends ExclusiveImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            String config = new RqPrint(req).printBody();
            GetSecretPostRequest request = READER.readValue(config);
            try {
                ServiceManager serviceManager = InstanceFactory.getInstance(ServiceManager.class);
                if (request.getSourceId() == null) {
                    return messageJson(400, "No source selected");
                }

                if (Strings.isNullOrEmpty(request.getRegion())) {
                    return messageJson(400, "Missing region");
                }

                final GetSecretResponse ret = serviceManager.call(request.getRegion(), (api) -> api.getSecret(request.getSourceId(),
                        new GetSecretRequest()
                                .emailHash(Hash.hash64(request.email.getBytes(StandardCharsets.UTF_8)))
                                .clientId(CLIENT_ID)
                                .code(request.getCode())
                                .codeVerifier(request.getCodeVerifier())));

                if (ret.getSecret() == null) {
                    return new RsWithStatus(new RsText(WRITER.writeValueAsString(new GetSecretPostResponse(ret.getAvailable(), false))), 200);
                }

                EncryptionKey key = ENCRYPTION_KEY_READER.readValue(ret.getSecret());
                EncryptionKey newKey = EncryptionKey.changeEncryptionPassword(request.getEmail(), request.getPassword(), key);

                final SourceResponse sourceResponse = serviceManager.call(request.getRegion(), (api) -> api.getSource(request.getSourceId()));

                EncryptionKey sourceKey;
                try {
                    sourceKey = ENCRYPTION_KEY_READER.readValue(sourceResponse.getKey());
                    if (!sourceKey.getPublicKey().equals(newKey.getPublicKey())) {
                        log.error("Restored key does not match existing public key");
                        return messageJson(500, "Key mismatch with restore");
                    }
                } catch (Exception exception) {
                    return messageJson(500, "Invalid key for restore");
                }

                newKey.setEncryptedAdditionalKeys(sourceKey.getEncryptedAdditionalKeys());

                if (restoreFromSource(sourceResponse.getName(),
                        request.getPassword(),
                        serviceManager,
                        sourceResponse,
                        InstanceFactory.getInstance(CommandLineModule.INSTALLATION_IDENTITY),
                        newKey.getPrivateKey(request.getPassword()))) {
                    return new RsWithStatus(new RsText(WRITER.writeValueAsString(new GetSecretPostResponse(true, true))), 200);
                } else {
                    return messageJson(400, "Failed to start rebuild");
                }
            } catch (IOException exc) {
                return sendApiFailureOn(exc);
            }
        }

        @Override
        protected String getBusyMessage() {
            return "Restoring private key from service";
        }
    }
}
