package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.PingGet.getCorsHeaders;

import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;
import org.takes.rs.RsWithHeaders;

import com.underscoreresearch.backup.configuration.InstanceFactory;

@Slf4j
public class PingPost extends JsonWrap {
    public PingPost() {
        super(new Implementation());
    }

    private static class Implementation extends BaseImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            InstanceFactory.reloadConfigurationWithSource();
            return new RsWithHeaders(messageJson(200, "Ok"), getCorsHeaders(req));
        }
    }
}
