package com.underscoreresearch.backup.block;

import java.io.IOException;

public interface BlockDownloader {
    byte[] downloadBlock(String blockHash) throws IOException;

    void shutdown();
}
