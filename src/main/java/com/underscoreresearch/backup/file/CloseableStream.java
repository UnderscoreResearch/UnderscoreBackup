package com.underscoreresearch.backup.file;

import java.io.Closeable;
import java.io.IOException;
import java.util.stream.Stream;

/**
 * Interface for a closeable stream of elements.
 * Provides access to a stream with automatic resource management
 * through the Closeable interface.
 *
 * @param <T> the type of elements in the stream
 */
public interface CloseableStream<T> extends Closeable {
    /**
     * Returns a stream of elements.
     *
     * @return a stream of elements
     */
    Stream<T> stream();

    /**
     * Sets whether errors should be reported as null values in the stream.
     *
     * @param reportErrorsAsNull true to report errors as null values, false to throw exceptions
     */
    void setReportErrorsAsNull(boolean reportErrorsAsNull);

    /**
     * Closes this stream, releasing any system resources associated with it.
     * Default implementation does nothing.
     *
     * @throws IOException if an I/O error occurs
     */
    default void close() throws IOException {
    }
}
