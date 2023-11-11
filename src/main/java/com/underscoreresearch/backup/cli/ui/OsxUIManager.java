package com.underscoreresearch.backup.cli.ui;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OsxUIManager extends FileUIManager {
    protected void openString(String path) {
        try {
            Runtime.getRuntime().exec(new String[]{"open", path});
        } catch (IOException e) {
            log.warn("Failed to open folder \"{}\"", path, e);
        }
    }
}
