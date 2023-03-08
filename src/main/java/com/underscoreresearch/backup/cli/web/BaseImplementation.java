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
public abstract class BaseImplementation extends ResponseDecodingTake {

    @Override
    public final Response act(Request req) throws Exception {
        return super.act(req);
    }
}
