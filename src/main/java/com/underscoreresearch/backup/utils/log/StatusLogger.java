package com.underscoreresearch.backup.utils.log;

/**
 * Interface for components that automatically provide status information.
 * This interface extends ManualStatusLogger and is used to mark classes that
 * should be automatically discovered and registered as status loggers by the system.
 * Implementations of this interface are automatically collected by the StateLogger.
 */
public interface StatusLogger extends ManualStatusLogger {
}
