package com.underscoreresearch.backup.file.implementation;

import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
public class AclPermissionManager implements FilePermissionManager {
    private static final ObjectReader READER = MAPPER.readerFor(AclPermissions.class);
    private static final ObjectWriter WRITER = MAPPER.writerFor(AclPermissions.class);

    private static int encodeFlag(AclEntryFlag flag) {
        return switch (flag) {
            case DIRECTORY_INHERIT -> 0x00001;
            case FILE_INHERIT -> 0x00002;
            case NO_PROPAGATE_INHERIT -> 0x00004;
            case INHERIT_ONLY -> 0x00008;
            default -> throw new IllegalArgumentException("Unknown permission: " + flag);
        };
    }

    private static int encodeType(AclEntryType type) {
        return switch (type) {
            case ALARM -> 0x100000;
            case ALLOW -> 0x200000;
            case AUDIT -> 0x400000;
            case DENY -> 0x800000;
            default -> throw new IllegalArgumentException("Unknown permission: " + type);
        };
    }

    private static int encodePermission(AclEntryPermission permission) {
        return switch (permission) {
            case APPEND_DATA -> 0x00010;
            case DELETE -> 0x00020;
            case DELETE_CHILD -> 0x00040;
            case READ_ACL -> 0x00080;
            case EXECUTE -> 0x00100;
            case READ_ATTRIBUTES -> 0x00200;
            case READ_DATA -> 0x00400;
            case WRITE_ACL -> 0x00800;
            case READ_NAMED_ATTRS -> 0x01000;
            case SYNCHRONIZE -> 0x02000;
            case WRITE_ATTRIBUTES -> 0x04000;
            case WRITE_DATA -> 0x08000;
            case WRITE_NAMED_ATTRS -> 0x10000;
            case WRITE_OWNER -> 0x20000;
            default -> throw new IllegalArgumentException("Unknown permission: " + permission);
        };
    }

    private static Set<AclEntryPermission> decodePermissions(int permissions) {
        Set<AclEntryPermission> result = new HashSet<>();

        if ((permissions & 0x00010) != 0) {
            result.add(AclEntryPermission.APPEND_DATA);
        }
        if ((permissions & 0x00020) != 0) {
            result.add(AclEntryPermission.DELETE);
        }
        if ((permissions & 0x00040) != 0) {
            result.add(AclEntryPermission.DELETE_CHILD);
        }
        if ((permissions & 0x00080) != 0) {
            result.add(AclEntryPermission.READ_ACL);
        }
        if ((permissions & 0x00100) != 0) {
            result.add(AclEntryPermission.EXECUTE);
        }
        if ((permissions & 0x00200) != 0) {
            result.add(AclEntryPermission.READ_ATTRIBUTES);
        }
        if ((permissions & 0x00400) != 0) {
            result.add(AclEntryPermission.READ_DATA);
        }
        if ((permissions & 0x00800) != 0) {
            result.add(AclEntryPermission.WRITE_ACL);
        }
        if ((permissions & 0x01000) != 0) {
            result.add(AclEntryPermission.READ_NAMED_ATTRS);
        }
        if ((permissions & 0x02000) != 0) {
            result.add(AclEntryPermission.SYNCHRONIZE);
        }
        if ((permissions & 0x04000) != 0) {
            result.add(AclEntryPermission.WRITE_ATTRIBUTES);
        }
        if ((permissions & 0x08000) != 0) {
            result.add(AclEntryPermission.WRITE_DATA);
        }
        if ((permissions & 0x10000) != 0) {
            result.add(AclEntryPermission.WRITE_NAMED_ATTRS);
        }
        if ((permissions & 0x20000) != 0) {
            result.add(AclEntryPermission.WRITE_OWNER);
        }

        return result;
    }

    private static Set<AclEntryFlag> decodeFlags(int flags) {
        Set<AclEntryFlag> result = new HashSet<>();

        if ((flags & 0x00001) != 0) {
            result.add(AclEntryFlag.DIRECTORY_INHERIT);
        }
        if ((flags & 0x00002) != 0) {
            result.add(AclEntryFlag.FILE_INHERIT);
        }
        if ((flags & 0x00004) != 0) {
            result.add(AclEntryFlag.NO_PROPAGATE_INHERIT);
        }
        if ((flags & 0x00008) != 0) {
            result.add(AclEntryFlag.INHERIT_ONLY);
        }

        return result;
    }

    private static AclEntryType decodeType(int type) {
        return switch (type & 0xf00000) {
            case 0x100000 -> AclEntryType.ALARM;
            case 0x200000 -> AclEntryType.ALLOW;
            case 0x400000 -> AclEntryType.AUDIT;
            case 0x800000 -> AclEntryType.DENY;
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }

    @Override
    public String getPermissions(Path path) {
        try {
            AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class);
            List<AclEntry> attributes = aclView.getAcl();
            List<Object[]> ret = new ArrayList<>(attributes.size());
            for (AclEntry entry : attributes) {
                int type = encodeType(entry.type());
                int permissions = entry.permissions().stream().mapToInt(AclPermissionManager::encodePermission)
                        .reduce(0, (a, b) -> a | b);
                int flags = entry.flags().stream().mapToInt(AclPermissionManager::encodeFlag)
                        .reduce(0, (a, b) -> a | b);
                ret.add(new Object[]{
                        entry.principal().getName(),
                        type | permissions | flags
                });
            }

            return WRITER.writeValueAsString(new AclPermissions(aclView.getOwner().getName(), ret));
        } catch (IOException exc) {
            log.warn("Failed to get permissions for {}", path, exc);
            return null;
        }
    }

    @Override
    public void setPermissions(Path path, String permissions) {
        if (permissions != null) {
            try {
                AclPermissions aclPermissions;
                try {
                    aclPermissions = READER.readValue(permissions);
                } catch (IOException exc) {
                    return;
                }
                List<AclEntry> entries = new ArrayList<>();

                UserPrincipalLookupService lookupService = path.getFileSystem().getUserPrincipalLookupService();

                for (Object[] aclEntry : aclPermissions.permissions) {
                    String principal = (String) aclEntry[0];
                    int options = (int) aclEntry[1];

                    entries.add(AclEntry.newBuilder()
                            .setType(decodeType(options))
                            .setPrincipal(lookupService.lookupPrincipalByName(principal))
                            .setPermissions(decodePermissions(options))
                            .setFlags(decodeFlags(options))
                            .build());
                }

                AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class);
                aclView.setAcl(entries);
                try {
                    aclView.setOwner(lookupService.lookupPrincipalByName(aclPermissions.owner));
                } catch (IOException exc) {
                    if (!aclView.getOwner().getName().equals(aclPermissions.owner)) {
                        log.warn("Failed to set owner of file {} to {}", path, aclPermissions.owner);
                    }
                }
            } catch (IOException exc) {
                log.warn("Failed to set permissions for {}", path, exc);
            }
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    private static class AclPermissions {
        @JsonProperty("o")
        String owner;
        @JsonProperty("p")
        List<Object[]> permissions;
    }
}
