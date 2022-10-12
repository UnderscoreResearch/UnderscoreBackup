package com.underscoreresearch.backup.cli.web;

import lombok.extern.slf4j.Slf4j;

import org.takes.HttpException;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.misc.Href;
import org.takes.rq.RqHref;
import org.takes.rq.RqMethod;

@Slf4j
public abstract class BaseImplementation implements Take {
    @Override
    public final Response act(Request req) throws Exception {
        Href href = new RqHref.Base(req).href();
        RqMethod.Base method = new RqMethod.Base(req);
        try {
            Response response = actualAct(req);
            log.debug("{} {}", method.method(), href);
            return response;
        } catch (HttpException httpException) {
            log.debug("{} {}: {}", method.method(), href, httpException.code());
            throw httpException;
        } catch (Exception exc) {
            log.warn("{} {}", method.method(), href, exc);
            throw exc;
        }
    }

    public abstract Response actualAct(Request req) throws Exception;
}
