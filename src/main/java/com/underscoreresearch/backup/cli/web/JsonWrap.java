package com.underscoreresearch.backup.cli.web;

import lombok.AllArgsConstructor;
import lombok.Data;

import org.takes.Response;
import org.takes.Take;
import org.takes.rs.RsText;
import org.takes.rs.RsWithStatus;
import org.takes.tk.TkWithType;
import org.takes.tk.TkWrap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class JsonWrap extends TkWrap {
    private static ObjectWriter WRITER = new ObjectMapper().writerFor(Message.class);

    @AllArgsConstructor
    @Data
    public static class Message {
        private String message;
    }

    public JsonWrap(Take take) {
        super(new TkWithType(take, "application/json"));
    }

    public static Response messageJson(int code, String message) {
        try {
            return new RsWithStatus(new RsText(WRITER.writeValueAsString(new Message(message))), code);
        } catch (JsonProcessingException e) {
            return new RsWithStatus(new RsText("{\"message\": \"Can't write error message\"}"), code);
        }
    }
}
