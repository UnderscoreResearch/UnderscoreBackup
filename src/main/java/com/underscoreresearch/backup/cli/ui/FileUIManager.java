package com.underscoreresearch.backup.cli.ui;

import static com.underscoreresearch.backup.io.IOUtils.createDirectory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;

@Slf4j
public class FileUIManager implements UIManager {
    @Override
    public void displayErrorMessage(String message) {
        writeNotification("error", message);
    }

    private void writeNotification(String location, String message) {
        File parentDirectory = new File(InstanceFactory.getInstance(CommandLineModule.NOTIFICATION_LOCATION));
        createDirectory(parentDirectory, true);
        File file = new File(parentDirectory, location);
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write(message);
        } catch (IOException e) {
            log.warn("Failed to write notification message", e);
        }
    }

    @Override
    public void displayInfoMessage(String message) {
        writeNotification("info", message);
    }

    @Override
    public void openFolder(File path) {
        openString(path.toString());
    }

    @Override
    public void openUri(URI uri) {
        openString(uri.toString());
    }

    protected void openString(String string) {
        writeNotification("open", string);
    }

    @Override
    public void setTooltip(String message) {
        writeNotification("tooltip", message);
    }
}
