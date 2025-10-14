package com.underscoreresearch.backup.ui.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.takes.Response;
import org.takes.Take;
import org.takes.rs.RsText;
import org.takes.rs.RsWithStatus;
import org.takes.rs.RsWithType;
import org.takes.tk.TkWrap;

import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Base wrapper for web API endpoints that provides common response handling functionality.
 * This class extends TkWrap to provide JSON response formatting and error message handling.
 */
public class BaseWrap extends TkWrap {
    private static final ObjectWriter WRITER = MAPPER.writerFor(Message.class);

    /**
     * Creates a new BaseWrap with the specified Take implementation.
     *
     * @param take The Take implementation to wrap
     */
    public BaseWrap(Take take) {
        super(take);
    }

    /**
     * Wraps a response with the application/json content type.
     *
     * @param response The response to wrap
     * @return The wrapped response with JSON content type
     */
    public static Response jsonResponse(Response response) {
        return new RsWithType(response, "application/json");
    }

    /**
     * Creates a JSON response with a message and status code.
     *
     * @param code The HTTP status code
     * @param message The message to include in the response
     * @return A JSON response with the specified message and status code
     */
    public static Response messageJson(int code, String message) {
        try {
            return jsonResponse(new RsWithStatus(new RsText(WRITER.writeValueAsString(new Message(message))), code));
        } catch (JsonProcessingException e) {
            return new RsWithStatus(new RsText("{\"message\": \"Can't write error message\"}"), code);
        }
    }

    /**
     * Simple message class for JSON responses.
     */
    @AllArgsConstructor
    @Data
    public static class Message {
        private String message;
    }
}
