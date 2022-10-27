package com.underscoreresearch.backup.cli.web;

import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;

@Slf4j
public class ShutdownGet extends JsonWrap {
    public ShutdownGet() {
        super(new Implementation());
    }

    private static class Implementation extends BaseImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            System.exit(0);
            return messageJson(200, "Shutting down");
        }
    }
}
