package com.underscoreresearch.backup.ui.web;

import com.underscoreresearch.backup.ui.desktop.UIHandler;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;
import org.takes.misc.Href;
import org.takes.rq.RqHref;
import org.takes.rq.RqMethod;

import java.io.Closeable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.underscoreresearch.backup.ui.web.BaseWrap.messageJson;

/**
 * Base implementation for web endpoints that require exclusive access.
 * This class ensures that only one request can be processed at a time,
 * preventing concurrent operations that might conflict with each other.
 */
@Slf4j
public abstract class ExclusiveImplementation extends BaseImplementation {
    private static final Lock lock = new ReentrantLock();
    private static String busyMessage;

    /**
     * Handles a request by acquiring an exclusive lock before processing.
     * If the lock cannot be acquired, returns a 409 Conflict response.
     *
     * @param req The HTTP request
     * @return The response to the request
     * @throws Exception If there's an error processing the request
     */
    @Override
    public Response act(Request req) throws Exception {
        if (lock.tryLock()) {
            busyMessage = getBusyMessage();
            try (Closeable ignore = UIHandler.registerTask(busyMessage, true)) {
                return super.act(req);
            } finally {
                busyMessage = null;
                lock.unlock();
            }
        } else {
            Href href = new RqHref.Base(req).href();
            RqMethod.Base method = new RqMethod.Base(req);
            String message = busyMessage;
            if (message == null) {
                message = "Service Unavailable";
            }
            log.warn("{} \"{}\" Unavailable because: {}", method.method(), href, message);
            return messageJson(409, message);
        }
    }

    /**
     * Gets the message to display when the system is busy.
     * This method must be implemented by subclasses.
     *
     * @return The busy message
     */
    abstract protected String getBusyMessage();
}
