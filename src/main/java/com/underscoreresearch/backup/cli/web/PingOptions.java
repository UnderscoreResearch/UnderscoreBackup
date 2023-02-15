package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.PingGet.getCorsHeaders;

import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;
import org.takes.rs.RsWithHeaders;
import org.takes.rs.RsWithStatus;

@Slf4j
public class PingOptions extends JsonWrap {
    public PingOptions() {
        super(new Implementation());
    }

    private static class Implementation extends BaseImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            return new RsWithHeaders(new RsWithStatus(200), getCorsHeaders());
        }
    }
}
