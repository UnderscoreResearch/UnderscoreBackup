package com.underscoreresearch.backup.file.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

class AclPermissionManagerTest {
    @Test
    public void test() throws IOException {
        File file = File.createTempFile("test", "test");
        file.deleteOnExit();
        for (FileStore store : file.toPath().getFileSystem().getFileStores()) {
            if (!store.supportsFileAttributeView(AclFileAttributeView.class))
                return;
        }
        AclPermissionManager aclPermissionManager = new AclPermissionManager();

        String permissions = aclPermissionManager.getPermissions(file.toPath());

        Files.getFileAttributeView(file.toPath(), AclFileAttributeView.class).setAcl(new ArrayList<>());

        assertNotEquals(permissions, aclPermissionManager.getPermissions(file.toPath()));

        aclPermissionManager.setPermissions(file.toPath(), permissions);

        String permissionAfter = aclPermissionManager.getPermissions(file.toPath());

        assertEquals(permissions, permissionAfter);
    }
}