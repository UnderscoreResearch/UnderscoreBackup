package com.underscoreresearch.backup.utils;

import lombok.NoArgsConstructor;

/**
 * Exception thrown when a processing operation is intentionally stopped or canceled.
 * This exception is used to signal that an operation should be terminated in a controlled manner,
 * rather than due to an error condition.
 */
@NoArgsConstructor
public class ProcessingStoppedException extends RuntimeException {
    /**
     * Constructs a new ProcessingStoppedException with the specified detail message.
     *
     * @param message The detail message
     */
    public ProcessingStoppedException(String message) {
        super(message);
    }

    /**
     * Constructs a new ProcessingStoppedException with the specified cause.
     *
     * @param cause The cause of the exception
     */
    public ProcessingStoppedException(Throwable cause) {
        super(cause);
    }
}
