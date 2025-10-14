package com.underscoreresearch.backup.utils.log;

import java.util.List;

/**
 * Interface for components that provide status information through status lines.
 * Implementations of this interface can report their status for monitoring and display purposes.
 * Different types of status loggers may have different persistence characteristics.
 *
 * Instances of this interface need to be manually registered using the StateLogger.addLogger and
 * subsequently unregistered with StateLogger.removeLogger.
 */
public interface ManualStatusLogger {
    /**
     * Resets the status information, clearing any accumulated status data.
     */
    void resetStatus();

    /**
     * Returns the type of status logger, which determines how the status information is handled.
     * By default, returns Type.NORMAL.
     *
     * @return The type of status logger
     */
    default Type type() {
        return Type.NORMAL;
    }

    /**
     * Allows the status logger to filter or modify the list of status lines before they are displayed.
     * Default implementation does nothing.
     *
     * @param lines The list of status lines to filter
     */
    default void filterItems(List<StatusLine> lines) {
    }

    /**
     * Returns the current status information as a list of status lines.
     *
     * @return A list of status lines representing the current status
     */
    List<StatusLine> status();

    /**
     * Enum defining the types of status loggers and their persistence characteristics.
     */
    enum Type {
        /**
         * Normal status that may be cleared or reset.
         */
        NORMAL,
        
        /**
         * Status derived from logs, typically with automatic expiration.
         */
        LOG,
        
        /**
         * Status that should be preserved until explicitly cleared.
         */
        PERMANENT
    }
}
