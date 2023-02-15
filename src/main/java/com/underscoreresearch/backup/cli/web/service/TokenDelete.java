package com.underscoreresearch.backup.cli.web.service;

import static com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl.sendApiFailureOn;

import java.io.IOException;

import org.takes.Request;
import org.takes.Response;

import com.underscoreresearch.backup.cli.web.BaseImplementation;
import com.underscoreresearch.backup.cli.web.JsonWrap;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.manifest.ServiceManager;

public class TokenDelete extends JsonWrap {
    public TokenDelete() {
        super(new Implementation());
    }

    private static class Implementation extends BaseImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {

            try {
                ServiceManager serviceManager = InstanceFactory.getInstance(ServiceManager.class);
                serviceManager.deleteToken();
                return messageJson(200, "Token deleted");
            } catch (IOException exc) {
                return sendApiFailureOn(exc);
            }
        }
    }
}
