package com.underscoreresearch.backup.file;

import java.nio.file.Path;

public interface FilePermissionManager {
    String getPermissions(Path path);

    void setPermissions(Path path, String permissions);
}
