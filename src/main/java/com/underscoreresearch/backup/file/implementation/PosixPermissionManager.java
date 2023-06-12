package com.underscoreresearch.backup.file.implementation;

import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.HashSet;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.file.FilePermissionManager;

@Slf4j
public class PosixPermissionManager implements FilePermissionManager {
    private static final ObjectReader READER = MAPPER.readerFor(PosixPermissions.class);
    private static final ObjectWriter WRITER = MAPPER.writerFor(PosixPermissions.class);

    private static int encodePermission(PosixFilePermission permission) {
        return switch (permission) {
            case OWNER_READ -> 0x400;
            case OWNER_WRITE -> 0x200;
            case OWNER_EXECUTE -> 0x100;
            case GROUP_READ -> 0x040;
            case GROUP_WRITE -> 0x020;
            case GROUP_EXECUTE -> 0x010;
            case OTHERS_READ -> 0x004;
            case OTHERS_WRITE -> 0x002;
            case OTHERS_EXECUTE -> 0x001;
            default -> throw new IllegalArgumentException("Unknown permission: " + permission);
        };
    }

    private static Set<PosixFilePermission> decodePermissions(int permissions) {
        Set<PosixFilePermission> result = new HashSet<>();

        if ((permissions & 0x400) != 0) {
            result.add(PosixFilePermission.OWNER_READ);
        }
        if ((permissions & 0x200) != 0) {
            result.add(PosixFilePermission.OWNER_WRITE);
        }
        if ((permissions & 0x100) != 0) {
            result.add(PosixFilePermission.OWNER_EXECUTE);
        }
        if ((permissions & 0x40) != 0) {
            result.add(PosixFilePermission.GROUP_READ);
        }
        if ((permissions & 0x20) != 0) {
            result.add(PosixFilePermission.GROUP_WRITE);
        }
        if ((permissions & 0x10) != 0) {
            result.add(PosixFilePermission.GROUP_EXECUTE);
        }
        if ((permissions & 0x4) != 0) {
            result.add(PosixFilePermission.OTHERS_READ);
        }
        if ((permissions & 0x2) != 0) {
            result.add(PosixFilePermission.OTHERS_WRITE);
        }
        if ((permissions & 0x1) != 0) {
            result.add(PosixFilePermission.OTHERS_EXECUTE);
        }

        return result;
    }

    @Override
    public String getPermissions(Path path) {
        try {
            PosixFileAttributeView posixView = Files.getFileAttributeView(path, PosixFileAttributeView.class);
            PosixFileAttributes attributes = posixView.readAttributes();
            Set<PosixFilePermission> permissions = attributes.permissions();

            return WRITER.writeValueAsString(new PosixPermissions(attributes.owner().getName(),
                    attributes.group().getName(),
                    permissions.stream().mapToInt(PosixPermissionManager::encodePermission)
                            .reduce(0, (a, b) -> a | b)));
        } catch (IOException exc) {
            log.warn("Failed to get permissions for {}", path, exc);
            return null;
        }
    }

    @Override
    public void setPermissions(Path path, String permissions) {
        if (permissions != null) {
            try {
                PosixPermissions posixPermissions;
                try {
                    posixPermissions = READER.readValue(permissions);
                } catch (IOException exc) {
                    return;
                }
                UserPrincipalLookupService lookupService = path.getFileSystem().getUserPrincipalLookupService();
                PosixFileAttributeView posixView = Files.getFileAttributeView(path, PosixFileAttributeView.class);
                posixView.setOwner(lookupService.lookupPrincipalByName(posixPermissions.owner));
                posixView.setGroup(lookupService.lookupPrincipalByGroupName(posixPermissions.group));
                posixView.setPermissions(decodePermissions(posixPermissions.permissions));
            } catch (IOException exc) {
                log.warn("Failed to set permissions for {}", path, exc);
            }
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    private static class PosixPermissions {
        @JsonProperty("o")
        String owner;
        @JsonProperty("g")
        String group;
        @JsonProperty("p")
        Integer permissions;
    }
}
