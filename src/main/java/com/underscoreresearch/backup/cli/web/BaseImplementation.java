package com.underscoreresearch.backup.cli.web;

import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;

@Slf4j
public abstract class BaseImplementation implements Take {
    @Override
    public Response act(Request req) throws Exception {
        return actualAct(req);
    }

    public abstract Response actualAct(Request req) throws Exception;
}
