package com.underscoreresearch.backup.io;

import java.io.IOException;
import java.util.List;

public interface IOIndex extends IOProvider {
    List<String> availableKeys(String prefix) throws IOException;
}
