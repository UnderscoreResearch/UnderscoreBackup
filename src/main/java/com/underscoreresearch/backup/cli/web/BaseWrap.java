package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import lombok.AllArgsConstructor;
import lombok.Data;

import org.takes.Response;
import org.takes.Take;
import org.takes.rs.RsText;
import org.takes.rs.RsWithStatus;
import org.takes.rs.RsWithType;
import org.takes.tk.TkWrap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;

public class BaseWrap extends TkWrap {
    private static final ObjectWriter WRITER = MAPPER.writerFor(Message.class);

    public BaseWrap(Take take) {
        super(take);
    }

    public static Response jsonResponse(Response response) {
        return new RsWithType(response, "application/json");
    }

    public static Response messageJson(int code, String message) {
        try {
            return jsonResponse(new RsWithStatus(new RsText(WRITER.writeValueAsString(new Message(message))), code));
        } catch (JsonProcessingException e) {
            return new RsWithStatus(new RsText("{\"message\": \"Can't write error message\"}"), code);
        }
    }

    @AllArgsConstructor
    @Data
    public static class Message {
        private String message;
    }
}
