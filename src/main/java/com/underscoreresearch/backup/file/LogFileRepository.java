package com.underscoreresearch.backup.file;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

public interface LogFileRepository extends Closeable {
    void addFile(String file) throws IOException;
    void resetFiles(List<String> files) throws IOException;
    List<String> getAllFiles() throws IOException;
    String getRandomFile() throws IOException;
}
