package com.underscoreresearch.backup.ui.web;

import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;

/**
 * Base implementation for web API endpoints.
 * This abstract class provides a common structure for handling web requests
 * in the backup system's web interface.
 */
@Slf4j
public abstract class BaseImplementation implements Take {
    /**
     * Handles a web request by delegating to the actualAct method.
     *
     * @param req The incoming request
     * @return The response to send back
     * @throws Exception If an error occurs while processing the request
     */
    @Override
    public Response act(Request req) throws Exception {
        return actualAct(req);
    }

    /**
     * Processes the actual request implementation.
     * This method must be implemented by subclasses to provide specific endpoint behavior.
     *
     * @param req The incoming request
     * @return The response to send back
     * @throws Exception If an error occurs while processing the request
     */
    public abstract Response actualAct(Request req) throws Exception;
}
