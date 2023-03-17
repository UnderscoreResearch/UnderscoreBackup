package com.underscoreresearch.backup.cli.web.service;

import static com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl.sendApiFailureOn;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.IOException;
import java.util.stream.Collectors;

import org.takes.Request;
import org.takes.Response;
import org.takes.misc.Href;
import org.takes.rq.RqHref;
import org.takes.rs.RsText;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.cli.web.BaseImplementation;
import com.underscoreresearch.backup.cli.web.JsonWrap;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.service.api.model.ListSourcesResponse;

public class SourcesGet extends JsonWrap {
    private static final ObjectWriter WRITER = MAPPER.writerFor(ListSourcesResponse.class);

    public SourcesGet() {
        super(new Implementation());
    }

    private static class Implementation extends BaseImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {

            try {
                ServiceManager serviceManager = InstanceFactory.getInstance(ServiceManager.class);
                final ListSourcesResponse ret = serviceManager.call(null, (api) -> api.listSources());
                final Href href = new RqHref.Base(req).href();
                final Iterable<String> excludeSelf = href.param("excludeSelf");
                if (excludeSelf != null) {
                    for (String par : excludeSelf)
                        if ("true".equals(par)) {
                            String identity = InstanceFactory.getInstance(CommandLineModule.INSTALLATION_IDENTITY);
                            ret.setSources(ret.getSources().stream().filter(source -> !source.getIdentity().equals(identity))
                                    .collect(Collectors.toList()));
                            break;
                        }
                }
                return new RsText(WRITER.writeValueAsString(ret));
            } catch (IOException exc) {
                return sendApiFailureOn(exc);
            }
        }
    }
}
