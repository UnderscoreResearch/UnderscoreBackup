package com.underscoreresearch.backup.ui.web.methods.service;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.ui.web.BaseImplementation;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.service.api.BackupApi;
import com.underscoreresearch.backup.service.api.model.ListSourcesResponse;
import com.underscoreresearch.backup.service.api.model.SourceResponse;
import org.takes.Request;
import org.takes.Response;
import org.takes.misc.Href;
import org.takes.rq.RqHref;

import java.io.IOException;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.ui.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl.sendApiFailureOn;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Endpoint for getting backup sources from the service.
 * This class retrieves information about backup sources registered with the service.
 */
public class SourcesGet extends BaseWrap {
    private static final ObjectWriter WRITER = MAPPER.writerFor(ListSourcesResponse.class);

    /**
     * Constructor for the SourcesGet endpoint.
     */
    public SourcesGet() {
        super(new Implementation());
    }

    /**
     * Implementation of the base implementation that handles the HTTP request.
     */
    private static class Implementation extends BaseImplementation {
        
        /**
         * Processes the HTTP request to retrieve sources.
         * 
         * @param req The HTTP request
         * @return Response containing the sources or an error message
         * @throws Exception If there's an error processing the request
         */
        @Override
        public Response actualAct(Request req) throws Exception {

            try {
                ServiceManager serviceManager = InstanceFactory.getInstance(ServiceManager.class);
                final Href href = new RqHref.Base(req).href();
                final Iterable<String> onlySelf = href.param("onlySelf");
                for (String par : onlySelf)
                    if ("true".equals(par)) {
                        ListSourcesResponse ret = new ListSourcesResponse();
                        if (serviceManager.getSourceId() != null) {
                            SourceResponse sourceResponse = serviceManager.call(null, (api) -> api.getSource(serviceManager.getSourceId()));
                            ret.setSources(Lists.newArrayList(sourceResponse));
                        } else {
                            ret.setSources(Lists.newArrayList());
                        }
                        return encryptResponse(req, WRITER.writeValueAsString(ret));
                    }
                final ListSourcesResponse ret = serviceManager.call(null, BackupApi::listSources);
                final Iterable<String> excludeSelf = href.param("excludeSelf");
                for (String par : excludeSelf)
                    if ("true".equals(par)) {
                        String identity = InstanceFactory.getInstance(CommandLineModule.INSTALLATION_IDENTITY);
                        ret.setSources(ret.getSources().stream().filter(source -> !source.getIdentity().equals(identity))
                                .collect(Collectors.toList()));
                        break;
                    }
                return encryptResponse(req, WRITER.writeValueAsString(ret));
            } catch (IOException exc) {
                return sendApiFailureOn(exc);
            }
        }
    }
}
