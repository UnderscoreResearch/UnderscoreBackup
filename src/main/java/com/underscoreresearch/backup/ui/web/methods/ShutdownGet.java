package com.underscoreresearch.backup.ui.web.methods;

import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;

/**
 * Handles HTTP GET requests for the shutdown endpoint.
 * This endpoint initiates a graceful shutdown of the application.
 */
@Slf4j
public class ShutdownGet extends BaseWrap {
    /**
     * Creates a new ShutdownGet instance with the Implementation handler.
     */
    public ShutdownGet() {
        super(new Implementation());
    }

    /**
     * Implementation of the shutdown endpoint that initiates system shutdown.
     */
    private static class Implementation extends ExclusiveImplementation {
        /**
         * Handles the shutdown request by starting a daemon thread that will exit the application
         * after a short delay to allow the response to be sent.
         *
         * @param req The HTTP request
         * @return A response with 200 OK status and a shutdown message
         * @throws Exception If there's an error processing the request
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            Thread thread = new Thread(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted while waiting for shutdown", e);
                }
                System.exit(0);
            }, "Shutdown thread");
            thread.setDaemon(true);
            thread.start();

            return messageJson(200, "Shutting down");
        }

        /**
         * Gets the message to display when the system is busy and cannot be shut down.
         *
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Shutting down";
        }
    }
}
