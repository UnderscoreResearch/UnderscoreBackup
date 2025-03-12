package com.underscoreresearch.backup.io;

import java.io.IOException;

public interface IOProvider {
    String upload(String suggestedKey, byte[] data) throws IOException;

    byte[] download(String key) throws IOException;

    String getCacheKey();

    boolean exists(String key) throws IOException;

    void delete(String key) throws IOException;

    void checkCredentials(boolean readonly) throws IOException;
}
