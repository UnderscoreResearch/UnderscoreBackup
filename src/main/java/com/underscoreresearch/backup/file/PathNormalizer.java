package com.underscoreresearch.backup.file;

import java.io.File;
import java.util.regex.Pattern;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.apache.commons.lang3.SystemUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PathNormalizer {

    public static final String PATH_SEPARATOR = "/";
    public static final String ROOT = "/";
    private static final Pattern RESOLVE_RELATIVE = Pattern.compile("/(([^/]+/\\.\\.(/|$))|(\\.(/|$)))+");
    private static final Pattern ROOTED = Pattern.compile("^([a-z0-9]:)?([\\\\/])", Pattern.CASE_INSENSITIVE);

    public static String normalizePath(final String path) {
        if (path.equals(ROOT) || path.equals(File.separator)) {
            return ROOT;
        }

        String ret;
        boolean directory = path.endsWith(File.separator);
        File file = new File(path);
        if (!ROOTED.matcher(path).find())
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

    private static String resolveRelative(String ret) {
        return RESOLVE_RELATIVE.matcher(ret).replaceAll(PATH_SEPARATOR);
    }

    public static String physicalPath(final String normalizedPath) {
        return normalizedPath.replace(PATH_SEPARATOR, File.separator);
    }

    public static String combinePaths(String base, String additional) {
        if (ROOT.equals(base) && SystemUtils.IS_OS_WINDOWS) {
            return additional;
        }
        if (base.endsWith(PATH_SEPARATOR)) {
            if (additional.startsWith(PATH_SEPARATOR))
                return base + additional.substring(1);
            else
                return base + additional;
        }
        return base + PATH_SEPARATOR + additional;
    }

    public static String normalizedPathParent(String file) {
        if (file.endsWith(PATH_SEPARATOR))
            file = file.substring(0, file.length() - 1);
        int index = file.lastIndexOf(PATH_SEPARATOR);
        if (index < 0)
            return "";
        return file.substring(0, index + 1);
    }
}
