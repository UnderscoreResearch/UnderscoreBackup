package com.underscoreresearch.backup.file;

import java.io.Closeable;

public abstract class CloseableLock implements Closeable {
    @Override
    public abstract void close();
}
