package com.underscoreresearch.backup.cli.web.service;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.cli.web.BaseImplementation;
import com.underscoreresearch.backup.cli.web.BaseWrap;
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

import static com.underscoreresearch.backup.cli.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl.sendApiFailureOn;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

public class SourcesGet extends BaseWrap {
    private static final ObjectWriter WRITER = MAPPER.writerFor(ListSourcesResponse.class);

    public SourcesGet() {
        super(new Implementation());
    }

    private static class Implementation extends BaseImplementation {
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
