package com.underscoreresearch.backup.utils.log;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.underscoreresearch.backup.ui.desktop.UIHandler;
import lombok.Getter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * A Log4j2 appender that captures log events and makes them available for status reporting.
 * This appender maintains separate queues for regular and error events, with automatic
 * expiration of old entries. It also integrates with the UI to display error messages.
 */
@Plugin(name = "ActivityAppender",
        category = Core.CATEGORY_NAME,
        elementType = Appender.ELEMENT_TYPE)
public class ActivityAppender extends AbstractAppender implements StatusLogger {
    private static final int MAX_ENTRIES = 100;

    private static final Map<String, ActivityAppender> APPENDERS = new HashMap<>();
    private final ConcurrentLinkedDeque<LogStatusLine> events = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<LogStatusLine> errorEvents = new ConcurrentLinkedDeque<>();

    /**
     * Constructs a new ActivityAppender with the specified name, filter, and layout.
     *
     * @param name   The name of the appender
     * @param filter The filter to apply to log events
     * @param layout The layout to format log events
     */
    protected ActivityAppender(String name, Filter filter, Layout<String> layout) {
        super(name, filter, layout, true, Property.EMPTY_ARRAY);
    }

    /**
     * Factory method to create or retrieve an ActivityAppender instance.
     * If an appender with the specified name already exists, it is returned.
     *
     * @param name   The name of the appender
     * @param filter The filter to apply to log events
     * @param layout The layout to format log events
     * @return The ActivityAppender instance
     */
    @PluginFactory
    public static synchronized ActivityAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter,
            @PluginElement("Layout") Layout<String> layout) {
        if (!APPENDERS.containsKey(name))
            APPENDERS.put(name, new ActivityAppender(name, filter, layout));
        return APPENDERS.get(name);
    }

    /**
     * Resets the logging state for all registered ActivityAppender instances.
     */
    public static synchronized void resetLogging() {
        APPENDERS.forEach((a, b) -> b.resetStatus());
    }

    /**
     * Returns the type of status logger.
     *
     * @return The status logger type
     */
    @Override
    public Type type() {
        return Type.LOG;
    }

    /**
     * Processes a log event, adding it to the appropriate queue based on its level.
     * Debug and trace events are ignored.
     *
     * @param event The log event to process
     */
    @Override
    public void append(LogEvent event) {
        if (event.getLevel() != Level.DEBUG && event.getLevel() != Level.TRACE) {
            if (event.getLevel() == Level.ERROR) {
                addEvent(errorEvents, event);
                UIHandler.displayErrorMessage(event.getMessage().getFormattedMessage());
            } else {
                addEvent(events, event);
            }
        }
    }

    /**
     * Adds a log event to the specified queue, maintaining the maximum size limit.
     *
     * @param currentEvents The queue to add the event to
     * @param event The log event to add
     */
    private void addEvent(ConcurrentLinkedDeque<LogStatusLine> currentEvents, LogEvent event) {
        while (currentEvents.size() >= MAX_ENTRIES)
            currentEvents.removeLast();

        currentEvents.addFirst(new LogStatusLine(event.getSource().getClassName(),
                event.getLevel().name(),
                getLayout().toSerializable(event).toString().trim()));
    }

    /**
     * Clears all stored log events.
     */
    @Override
    public void resetStatus() {
        events.clear();
        errorEvents.clear();
    }

    /**
     * Returns a combined list of error and regular log events, sorted by timestamp.
     * Expired error events are removed before returning the list.
     *
     * @return A list of status lines representing log events
     */
    @Override
    public List<StatusLine> status() {
        while (!errorEvents.isEmpty() && errorEvents.getLast().getExpire().isBefore(Instant.now())) {
            // Don't remove an error that is newer than the last saved regular log error.
            if (!events.isEmpty() && errorEvents.getLast().getExpire().isAfter(events.getLast().getExpire()))
                break;
            errorEvents.removeLast();
        }

        List<StatusLine> ret = new ArrayList<>();
        ret.addAll(errorEvents);
        ret.addAll(events);
        ret.sort((a, b) -> ((LogStatusLine) b).getExpire().compareTo(((LogStatusLine) a).getExpire()));
        return ret;
    }

    /**
     * Represents a log event as a status line with an expiration time.
     */
    private static class LogStatusLine extends StatusLine {
        @Getter
        @JsonIgnore
        private final Instant expire;

        /**
         * Creates a new LogStatusLine with the specified reporter, code, and message.
         * Sets an expiration time 12 hours in the future.
         *
         * @param reporter The class that reported the log event
         * @param code The log level as a string
         * @param message The log message
         */
        public LogStatusLine(String reporter, String code, String message) {
            super(reporter, code, message);

            this.expire = Instant.now().plus(Duration.ofHours(12));
        }
    }
}
