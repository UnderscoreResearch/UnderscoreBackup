package com.underscoreresearch.backup.file;

import java.io.File;
import java.io.IOException;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PathNormalizer {

    public static final String PATH_SEPARATOR = "/";
    public static final String ROOT = "/";

    public static String normalizePath(final String path) {
        if (path.equals(ROOT) || path.equals(File.separator)) {
            return ROOT;
        }

        String ret;
        boolean directory = path.endsWith(File.separator);
        try {
            File file = new File(path);
            if (!file.isAbsolute())
                file = new File(System.getProperty("user.dir"), path);
            file = file.getCanonicalFile();
            if (file.exists()) {
                directory = file.isDirectory();
            }
            ret = file.getAbsolutePath();
        } catch (IOException exc) {
            ret = path;
        }
        ret = ret.replace(File.separator, PATH_SEPARATOR);
        if (ret.endsWith(PATH_SEPARATOR)) {
            if (!directory && ret.length() > 1) {
                ret = ret.substring(0, ret.length() - 1);
            }
        } else if (directory) {
            ret += PATH_SEPARATOR;
        }
        return ret;
    }

    public static String physicalPath(final String normalizedPath) {
        return normalizedPath.replace(PATH_SEPARATOR, File.separator);
    }
}
