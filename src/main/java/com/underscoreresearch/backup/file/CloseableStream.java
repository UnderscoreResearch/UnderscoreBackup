package com.underscoreresearch.backup.file;

import java.io.Closeable;
import java.io.IOException;
import java.util.stream.Stream;

public interface CloseableStream<T> extends Closeable {
    Stream<T> stream();

    void setReportErrorsAsNull(boolean reportErrorsAsNull);

    default void close() throws IOException {
    }
}
