package com.underscoreresearch.backup.io;

import java.io.IOException;
import java.util.List;

public interface IOIndex {
    List<String> availableKeys(String prefix) throws IOException;
}
