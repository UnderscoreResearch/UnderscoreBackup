package com.underscoreresearch.backup.file.implementation;

import com.underscoreresearch.backup.file.FilePermissionManager;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static com.underscoreresearch.backup.utils.log.LogUtil.debug;

/**
 * Extension of PermissionFileSystemAccess for Windows file systems.
 * Provides Windows-specific implementation for checking symbolic links.
 */
@Slf4j
public class WindowsFileSystemAccess extends PermissionFileSystemAccess {
    /**
     * Constructor for WindowsFileSystemAccess.
     *
     * @param permissionManager The permission manager to use for file permissions
     */
    public WindowsFileSystemAccess(FilePermissionManager permissionManager) {
        super(permissionManager);
    }

    /**
     * Checks if a path is a symbolic link.
     * On Windows, this needs special handling because symbolic links can appear as both
     * symbolic links and as "other" directory types.
     *
     * @param filePath The path to check
     * @return true if the path is a symbolic link, false otherwise
     */
    @Override
    protected boolean isSymbolicLink(Path filePath) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return (attrs.isDirectory() && attrs.isOther()) || attrs.isSymbolicLink();
        } catch (IOException e) {
            debug(() -> log.debug("Failed to determine symbolic link state of \"{}\"", filePath.toAbsolutePath(), e));
            return true;
        }
    }
}
