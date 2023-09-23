package com.underscoreresearch.backup.file.implementation;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.file.FilePermissionManager;

import static com.underscoreresearch.backup.utils.LogUtil.debug;

@Slf4j
public class WindowsFileSystemAccess extends PermissionFileSystemAccess {
    public WindowsFileSystemAccess(FilePermissionManager permissionManager) {
        super(permissionManager);
    }

    @Override
    protected boolean isSymbolicLink(Path filePath) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return attrs.isDirectory() && attrs.isOther();
        } catch (IOException e) {
            debug(() -> log.debug("Failed to determine symbolic link state of {}", filePath.toAbsolutePath(), e));
            return true;
        }
    }
}
