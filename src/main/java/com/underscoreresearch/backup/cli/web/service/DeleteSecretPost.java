package com.underscoreresearch.backup.cli.web.service;

import static com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl.sendApiFailureOn;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;

import com.underscoreresearch.backup.cli.web.ExclusiveImplementation;
import com.underscoreresearch.backup.cli.web.JsonWrap;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.service.api.model.SourceResponse;

@Slf4j
public class DeleteSecretPost extends JsonWrap {
    public DeleteSecretPost() {
        super(new Implementation());
    }

    private static class Implementation extends ExclusiveImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            try {
                ServiceManager serviceManager = InstanceFactory.getInstance(ServiceManager.class);
                if (serviceManager.getSourceId() == null) {
                    return messageJson(400, "No source selected");
                }

                SourceResponse source = serviceManager.call(null, (api) -> api.getSource(serviceManager.getSourceId()));
                if (source.getSecretRegion() != null) {
                    serviceManager.call(source.getSecretRegion(), (api) -> api.deleteSecret(serviceManager.getSourceId()));
                }
                return messageJson(200, "Secret deleted");
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
