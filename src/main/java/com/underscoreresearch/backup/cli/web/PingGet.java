package com.underscoreresearch.backup.cli.web;

import java.io.IOException;
import java.util.Iterator;

import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;
import org.takes.rq.RqHeaders;
import org.takes.rs.RsWithHeaders;

@Slf4j
public class PingGet extends JsonWrap {
    public PingGet() {
        super(new Implementation());
    }

    public static String[] getCorsHeaders(Request req) throws IOException {
        return new String[]{
                "Access-Control-Allow-Origin: " + getSiteUrl(req),
                "Access-Control-Allow-Methods: GET, OPTIONS",
        };
    }

    public static String getSiteUrl(Request req) throws IOException {
        if (req != null) {
            final Iterator<String> headers = new RqHeaders.Smart(req)
                    .header("origin").iterator();
            if (headers.hasNext()) {
                final String origin = headers.next();
                if ("https://www.underscorebackup.com".equals(origin)) {
                    return origin;
                }
            }
        }

        return ("true".equals(System.getenv("BACKUP_DEV")) ? "https://dev.underscorebackup.com" : "https://underscorebackup.com");
    }

    private static class Implementation extends BaseImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            return new RsWithHeaders(messageJson(200, "Ok"), getCorsHeaders(req));
        }
    }
}
