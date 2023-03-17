package com.underscoreresearch.backup.cli.web.service;

import static com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl.sendApiFailureOn;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.IOException;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.takes.Request;
import org.takes.Response;
import org.takes.rq.RqPrint;
import org.takes.rs.RsText;

import com.fasterxml.jackson.databind.ObjectReader;
import com.underscoreresearch.backup.cli.commands.VersionCommand;
import com.underscoreresearch.backup.cli.web.ExclusiveImplementation;
import com.underscoreresearch.backup.cli.web.JsonWrap;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.service.api.model.SourceRequest;
import com.underscoreresearch.backup.service.api.model.SourceResponse;

public class SourcesPost extends JsonWrap {
    private static final ObjectReader READER = MAPPER.readerFor(CreateSourceRequest.class);

    public SourcesPost() {
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
    public static class CreateSourceRequest {
        private String name;
    }

    @Data
    @AllArgsConstructor
    public static class CreateSourceResponse {
        private String sourceId;
    }

    private static class Implementation extends ExclusiveImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            String config = new RqPrint(req).printBody();
            CreateSourceRequest request = READER.readValue(config);
            try {
                ServiceManager serviceManager = InstanceFactory.getInstance(ServiceManager.class);
                serviceManager.setSourceId(null);
                serviceManager.setSourceName(request.getName());
                EncryptionKey key = encryptionKey();
                if (InstanceFactory.hasConfiguration(false) && key != null) {
                    InstanceFactory.getInstance(ManifestManager.class).updateServiceSourceData(key);
                } else {
                    String identity = InstanceFactory.getInstance(CommandLineModule.INSTALLATION_IDENTITY);
                    SourceResponse ret = serviceManager.call(null, (api) -> api.createSource(new SourceRequest()
                            .name(serviceManager.getSourceName())
                            .version(VersionCommand.getVersion() + VersionCommand.getEdition())
                            .identity(identity)));
                    serviceManager.setSourceId(ret.getSourceId());
                }
                return new RsText(MAPPER.writeValueAsString(new CreateSourceResponse(serviceManager.getSourceId())));
            } catch (IOException exc) {
                return sendApiFailureOn(exc);
            }
        }

        @Override
        protected String getBusyMessage() {
            return "Creating source";
        }
    }
}
