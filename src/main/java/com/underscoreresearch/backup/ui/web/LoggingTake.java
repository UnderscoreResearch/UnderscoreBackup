package com.underscoreresearch.backup.ui.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.takes.HttpException;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.misc.Href;
import org.takes.rq.RqHref;
import org.takes.rq.RqMethod;

import static com.underscoreresearch.backup.utils.log.LogUtil.debug;

/**
 * A wrapper for Take implementations that adds logging functionality.
 * This class logs HTTP requests and responses, including any errors that occur.
 */
@Slf4j
@RequiredArgsConstructor
public class LoggingTake implements Take {
    private final Take take;

    /**
     * Handles a request by logging it and delegating to the wrapped Take implementation.
     * Logs errors that occur during processing.
     *
     * @param req The HTTP request
     * @return The response from the wrapped Take
     * @throws Exception If there's an error processing the request
     */
    @Override
    public Response act(Request req) throws Exception {
        Href href = new RqHref.Base(req).href();
        String method = new RqMethod.Base(req).method();
        try {
            if (!href.path().endsWith("/api/activity")) {
                debug(() -> log.debug("{} \"{}\"", method, href));
            }
            return take.act(req);
        } catch (HttpException httpException) {
            debug(() -> log.debug("{} \"{}\": {}", method, href, httpException.code()));
            throw httpException;
        } catch (Throwable exc) {
            log.error("{} \"{}\": 500", method, href, exc);
            throw exc;
        }
    }
}
