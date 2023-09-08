package com.underscoreresearch.backup.cli.web;

import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;

@Slf4j
public class ShutdownGet extends BaseWrap {
    public ShutdownGet() {
        super(new Implementation());
    }

    private static class Implementation extends ExclusiveImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            new Thread(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    log.error("Interrupted while waiting for shutdown", e);
                }
                System.exit(0);
            }, "Shutdown thread").start();
            return messageJson(200, "Shutting down");
        }

        @Override
        protected String getBusyMessage() {
            return "Shutting down";
        }
    }
}
