package com.underscoreresearch.backup.cli.web;

import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;
import org.takes.rs.RsWithHeaders;

@Slf4j
public class PingGet extends JsonWrap {
    public PingGet() {
        super(new Implementation());
    }

    public static String[] getCorsHeaders() {
        return new String[]{
                "Access-Control-Allow-Origin: " + getSiteUrl(),
                "Access-Control-Allow-Methods: GET, OPTIONS",
        };
    }

    public static String getSiteUrl() {
        return ("true".equals(System.getenv("BACKUP_DEV")) ? "https://dev.underscorebackup.com" : "https://underscorebackup.com");
    }

    private static class Implementation extends BaseImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            return new RsWithHeaders(messageJson(200, "Ok"), getCorsHeaders());
        }
    }
}
