package com.underscoreresearch.backup.file.implementation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.file.FilePermissionManager;

@Slf4j
public class WindowsFileSystemAccess extends PermissionFileSystemAccess {
    public WindowsFileSystemAccess(FilePermissionManager permissionManager) {
        super(permissionManager);
    }

    @Override
    protected boolean isSymbolicLink(Path filePath) {
        try {
            if (!filePath.equals(filePath.toRealPath())) {
                return true;
            }
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return attrs.isDirectory() && attrs.isOther();
        } catch (IOException e) {
            log.warn("Failed to get real path for {}", filePath.toAbsolutePath(), e);
            return true;
        }
    }
}
