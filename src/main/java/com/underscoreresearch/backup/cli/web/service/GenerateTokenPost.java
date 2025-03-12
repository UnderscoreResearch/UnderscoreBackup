package com.underscoreresearch.backup.cli.web.service;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.cli.commands.VersionCommand;
import com.underscoreresearch.backup.cli.web.BaseWrap;
import com.underscoreresearch.backup.cli.web.ExclusiveImplementation;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.service.api.BackupApi;
import com.underscoreresearch.backup.service.api.model.ListSourcesResponse;
import com.underscoreresearch.backup.service.api.model.SourceRequest;
import com.underscoreresearch.backup.service.api.model.SourceResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.takes.Request;
import org.takes.Response;

import java.io.IOException;
import java.util.Optional;

import static com.underscoreresearch.backup.cli.web.PsAuthedContent.decodeRequestBody;
import static com.underscoreresearch.backup.cli.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.cli.web.service.CreateSecretPut.encryptionIdentity;
import static com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl.sendApiFailureOn;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

public class GenerateTokenPost extends BaseWrap {
    private static final ObjectReader READER = MAPPER.readerFor(InternalGenerateTokenRequest.class);
    private static final ObjectWriter WRITER = MAPPER.writerFor(InternalGenerateTokenResponse.class);

    public GenerateTokenPost() {
        super(new Implementation());
    }

    @Data
    private static class InternalGenerateTokenRequest {
        private String codeVerifier;
        private String code;
        private String sourceName;
    }

    @Data
    @AllArgsConstructor
    private static class InternalGenerateTokenResponse {
        private String token;
    }

    private static class Implementation extends ExclusiveImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            InternalGenerateTokenRequest request = READER.readValue(decodeRequestBody(req));

            try {
                ServiceManager serviceManager = InstanceFactory.getInstance(ServiceManager.class);
                serviceManager.generateToken(request.getCode(),
                        request.getCodeVerifier());
                ListSourcesResponse sources = serviceManager.call(null, BackupApi::listSources);
                String identity = InstanceFactory.getInstance(CommandLineModule.INSTALLATION_IDENTITY);
                Optional<SourceResponse> found = sources.getSources().stream()
                        .filter(source -> source.getIdentity().equals(identity)).findAny();
                if (found.isPresent()) {
                    serviceManager.setSourceId(found.get().getSourceId());
                    serviceManager.setSourceName(found.get().getName());
                } else if (InstanceFactory.hasConfiguration(false) && encryptionIdentity() != null) {
                    SourceResponse created = serviceManager.call(null, (api) -> api
                            .createSource(new SourceRequest().name(serviceManager.getSourceName())
                                    .version(VersionCommand.getVersionEdition())
                                    .identity(identity)));
                    serviceManager.setSourceId(created.getSourceId());
                }
                return encryptResponse(req, WRITER.writeValueAsString(new InternalGenerateTokenResponse(
                        serviceManager.getToken())));
            } catch (IOException exc) {
                return sendApiFailureOn(exc);
            }
        }

        @Override
        protected String getBusyMessage() {
            return "Generating service token";
        }
    }
}
