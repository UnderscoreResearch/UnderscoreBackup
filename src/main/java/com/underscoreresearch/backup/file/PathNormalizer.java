package com.underscoreresearch.backup.file;

import java.io.File;
import java.io.IOException;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PathNormalizer {

    public static final String PATH_SEPARATOR = "/";

    public static String normalizePath(final String path) {
        if (path.equals("/") || path.equals(File.separator)) {
            return "/";
        }

        String ret;
        try {
            File file = new File(System.getProperty("user.dir"), path).getCanonicalFile();
            ret = file.toPath().toRealPath().toString();
        } catch (IOException exc) {
            ret = path;
        }
        ret = ret.replace(File.separator, PATH_SEPARATOR);
        if (path.endsWith(File.separator) && !ret.endsWith(PATH_SEPARATOR)) {
            ret += PATH_SEPARATOR;
        }
        return ret;
    }

    public static String physicalPath(final String normalizedPath) {
        return normalizedPath.replace(PATH_SEPARATOR, File.separator);
    }
}
