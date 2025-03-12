package com.underscoreresearch.backup.file.implementation;

import org.junit.jupiter.api.Test;
import org.mockito.internal.util.collections.Sets;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PosixPermissionManagerTest {
    @Test
    public void test() throws IOException {
        File file = File.createTempFile("test", "test");
        file.deleteOnExit();
        for (FileStore store : file.toPath().getFileSystem().getFileStores()) {
            if (!store.supportsFileAttributeView(PosixFileAttributeView.class))
                return;
        }
        PosixPermissionManager posixPermissionManager = new PosixPermissionManager();

        String permissions = posixPermissionManager.getPermissions(file.toPath());

        Files.getFileAttributeView(file.toPath(), PosixFileAttributeView.class).setPermissions(Sets.newSet(PosixFilePermission.OWNER_WRITE));
        assertNotEquals(permissions, posixPermissionManager.getPermissions(file.toPath()));

        posixPermissionManager.setPermissions(file.toPath(), permissions);

        assertEquals(permissions, posixPermissionManager.getPermissions(file.toPath()));
    }
}