package com.underscoreresearch.backup.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.underscoreresearch.backup.cli.ui.UIHandler;

@Plugin(name = "ActivityAppender",
        category = Core.CATEGORY_NAME,
        elementType = Appender.ELEMENT_TYPE)
public class ActivityAppender extends AbstractAppender implements StatusLogger {
    private static final int MAX_ENTRIES = 100;

    private static final Map<String, ActivityAppender> APPENDERS = new HashMap<>();
    private final ConcurrentLinkedDeque<LogStatusLine> events = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<LogStatusLine> errorEvents = new ConcurrentLinkedDeque<>();

    protected ActivityAppender(String name, Filter filter, Layout<String> layout) {
        super(name, filter, layout, true, Property.EMPTY_ARRAY);
    }

    @PluginFactory
    public static synchronized ActivityAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter,
            @PluginElement("Layout") Layout<String> layout) {
        if (!APPENDERS.containsKey(name))
            APPENDERS.put(name, new ActivityAppender(name, filter, layout));
        return APPENDERS.get(name);
    }

    public static synchronized void resetLogging() {
        APPENDERS.forEach((a, b) -> b.resetStatus());
    }

    @Override
    public Type type() {
        return Type.LOG;
    }

    @Override
    public void append(LogEvent event) {
        if (event.getLevel() != Level.DEBUG && event.getLevel() != Level.TRACE) {
            if (event.getLevel() == Level.ERROR) {
                addEvent(errorEvents, event);
                UIHandler.displayErrorMessage(event.getMessage().getFormattedMessage());
            } else {
                addEvent(events, event);
            }
            if (!errorEvents.isEmpty() && errorEvents.getLast().getExpire().isBefore(Instant.now())) {
                errorEvents.removeLast();
            }
        }
    }

    private void addEvent(ConcurrentLinkedDeque<LogStatusLine> currentEvents, LogEvent event) {
        while (currentEvents.size() >= MAX_ENTRIES)
            currentEvents.removeLast();
        currentEvents.addFirst(new LogStatusLine(event.getSource().getClassName(),
                event.getLevel().name(),
                getLayout().toSerializable(event).toString().trim()));
    }

    @Override
    public void resetStatus() {
        events.clear();
        errorEvents.clear();
    }

    @Override
    public List<StatusLine> status() {
        List<StatusLine> ret = new ArrayList<>();
        ret.addAll(errorEvents);
        ret.addAll(events);
        ret.sort((a, b) -> ((LogStatusLine)b).getExpire().compareTo(((LogStatusLine)a).getExpire()));
        return ret;
    }

    private static class LogStatusLine extends StatusLine {
        @Getter
        @JsonIgnore
        private final Instant expire;

        public LogStatusLine(String reporter, String code, String message) {
            super(reporter, code, message);

            this.expire = Instant.now().plus(Duration.ofHours(12));
        }
    }
}