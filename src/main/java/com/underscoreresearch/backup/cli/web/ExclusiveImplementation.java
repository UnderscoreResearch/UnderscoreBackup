package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.JsonWrap.messageJson;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;
import org.takes.misc.Href;
import org.takes.rq.RqHref;
import org.takes.rq.RqMethod;

@Slf4j
public abstract class ExclusiveImplementation extends BaseImplementation {
    private static final Lock lock = new ReentrantLock();
    private static String busyMessage;

    @Override
    public final Response act(Request req) throws Exception {
        if (lock.tryLock()) {
            busyMessage = getBusyMessage();
            try {
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
            log.warn("{} {} Unavailable because: {}", method.method(), href, message);
            return messageJson(409, message);
        }
    }

    abstract protected String getBusyMessage();
}
