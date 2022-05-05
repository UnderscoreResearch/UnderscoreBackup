package com.underscoreresearch.backup.utils;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class NonClosingInputStream extends InputStream {
    final private InputStream stream;

    public NonClosingInputStream(InputStream stream) {
        this.stream = stream;
    }

    @Override
    public int read() throws IOException {
        return stream.read();
    }

    @Override
    public int read(@NotNull byte[] b) throws IOException {
        return stream.read(b);
    }

    @Override
    public int read(@NotNull byte[] b, int off, int len) throws IOException {
        return stream.read(b, off, len);
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        return stream.readAllBytes();
    }

    @Override
    public byte[] readNBytes(int len) throws IOException {
        return stream.readNBytes(len);
    }

    @Override
    public int readNBytes(byte[] b, int off, int len) throws IOException {
        return stream.readNBytes(b, off, len);
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public int available() throws IOException {
        return stream.available();
    }

    @Override
    public void mark(int readlimit) {
        stream.mark(readlimit);
    }

    @Override
    public boolean markSupported() {
        return stream.markSupported();
    }

    @Override
    public long transferTo(OutputStream out) throws IOException {
        return stream.transferTo(out);
    }

    @Override
    public long skip(long n) throws IOException {
        return stream.skip(n);
    }

    @Override
    public synchronized void reset() throws IOException {
        stream.reset();
    }
}
