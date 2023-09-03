package com.underscoreresearch.backup.cli.ui;

import java.io.File;
import java.net.URI;

public interface UIManager {
    void displayErrorMessage(String message);

    void displayInfoMessage(String message);

    void openFolder(File path);

    void openUri(URI uri);

    void setTooltip(String message);
}
