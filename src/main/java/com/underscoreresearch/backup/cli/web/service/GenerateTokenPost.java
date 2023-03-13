package com.underscoreresearch.backup.cli.web.service;

import static com.underscoreresearch.backup.cli.web.service.SourcesPost.encryptionKey;
import static com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl.sendApiFailureOn;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.IOException;
import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Data;

import org.takes.Request;
import org.takes.Response;
import org.takes.rq.RqPrint;
import org.takes.rs.RsText;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.cli.commands.VersionCommand;
import com.underscoreresearch.backup.cli.web.ExclusiveImplementation;
import com.underscoreresearch.backup.cli.web.JsonWrap;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl;
import com.underscoreresearch.backup.service.api.model.ListSourcesResponse;
import com.underscoreresearch.backup.service.api.model.SourceRequest;
import com.underscoreresearch.backup.service.api.model.SourceResponse;

public class GenerateTokenPost extends JsonWrap {
    private static ObjectReader READER = MAPPER.readerFor(InternalGenerateTokenRequest.class);
    private static ObjectWriter WRITER = MAPPER.writerFor(InternalGenerateTokenResponse.class);

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
            InternalGenerateTokenRequest request = READER.readValue(new RqPrint(req).printBody());

            try {
                ServiceManager serviceManager = InstanceFactory.getInstance(ServiceManager.class);
                serviceManager.generateToken(request.getCode(),
                        request.getCodeVerifier());
                ListSourcesResponse sources = serviceManager.call(null, (api) -> api.listSources());
                String identity = InstanceFactory.getInstance(CommandLineModule.INSTALLATION_IDENTITY);
                Optional<SourceResponse> found = sources.getSources().stream()
                        .filter(source -> source.getIdentity().equals(identity)).findAny();
                if (found.isPresent()) {
                    serviceManager.setSourceId(found.get().getSourceId());
                    serviceManager.setSourceName(found.get().getName());
                } else if (InstanceFactory.hasConfiguration(false) && encryptionKey() != null) {
                    SourceResponse created = serviceManager.call(null, (api) -> api
                            .createSource(new SourceRequest().name(serviceManager.getSourceName())
                                    .version(VersionCommand.getVersion() + VersionCommand.getEdition())
                                    .identity(identity)));
                    serviceManager.setSourceId(created.getSourceId());
                }
                return new RsText(WRITER.writeValueAsString(new InternalGenerateTokenResponse(
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
