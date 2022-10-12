package com.underscoreresearch.backup.file;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.SystemUtils;

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
        File file = new File(path);
        if (!file.isAbsolute() && !path.startsWith(File.separator))
            file = new File(System.getProperty("user.dir"), path);
        if (file.exists()) {
            directory = file.isDirectory();
        }
        ret = file.getPath().replace(File.separator, PATH_SEPARATOR);
        ret = resolveRelative(ret);
        if (ret.endsWith(PATH_SEPARATOR)) {
            if (!directory && ret.length() > 1) {
                ret = ret.substring(0, ret.length() - 1);
            }
        } else if (directory) {
            ret += PATH_SEPARATOR;
        }
        return ret;
    }

    private static Pattern RESOLVE_RELATIVE = Pattern.compile("/(([^/]+/\\.\\.(/|$))|(\\.(/|$)))+");

    private static String resolveRelative(String ret) {
        return RESOLVE_RELATIVE.matcher(ret).replaceAll("/");
    }

    public static String physicalPath(final String normalizedPath) {
        return normalizedPath.replace(PATH_SEPARATOR, File.separator);
    }
}
