package com.underscoreresearch.backup.cli.web;

import com.underscoreresearch.backup.cli.ui.UIHandler;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;
import org.takes.misc.Href;
import org.takes.rq.RqHref;
import org.takes.rq.RqMethod;

import java.io.Closeable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.underscoreresearch.backup.cli.web.BaseWrap.messageJson;

@Slf4j
public abstract class ExclusiveImplementation extends BaseImplementation {
    private static final Lock lock = new ReentrantLock();
    private static String busyMessage;

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

    abstract protected String getBusyMessage();
}
