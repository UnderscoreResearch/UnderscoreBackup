package com.underscoreresearch.backup.file;

import com.google.common.base.Strings;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.SystemUtils;

import java.io.File;
import java.util.regex.Pattern;

/**
 * Utility class for normalizing file paths across different operating systems and internal format.
 *
 * A normalized path uses forward slashes ('/') as separators and resolves relative paths. Also, '/' always represents
 * the root even on Windows (So / can have a child of C:/).
 *
 * Provides methods to convert between platform-specific paths and normalized paths.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PathNormalizer {

    public static final String PATH_SEPARATOR = "/";
    public static final String ROOT = "/";
    private static final Pattern RESOLVE_RELATIVE = Pattern.compile("/(([^/]+/\\.\\.(/|$))|(\\.(/|$)))+");
    private static final Pattern ROOTED = Pattern.compile("^([a-z0-9]:)?([\\\\/])", Pattern.CASE_INSENSITIVE);

    /**
     * Normalizes a file path to use standard separators and resolve relative paths.
     *
     * @param path The path to normalize
     * @return The normalized path
     */
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

    /**
     * Resolves relative path components like ".." and ".".
     *
     * @param ret The path to resolve
     * @return The resolved path
     */
    private static String resolveRelative(String ret) {
        return RESOLVE_RELATIVE.matcher(ret).replaceAll(PATH_SEPARATOR);
    }

    /**
     * Converts a normalized path to a platform-specific physical path.
     *
     * @param normalizedPath The normalized path to convert
     * @return The platform-specific path
     */
    public static String physicalPath(final String normalizedPath) {
        return normalizedPath.replace(PATH_SEPARATOR, File.separator);
    }

    /**
     * Combines a base path with an additional path component.
     *
     * @param base The base path
     * @param additional The additional path component
     * @return The combined path
     */
    public static String combinePaths(String base, String additional) {
        if (ROOT.equals(base) && SystemUtils.IS_OS_WINDOWS) {
            return additional;
        }
        if (Strings.isNullOrEmpty(base)) {
            if (SystemUtils.IS_OS_WINDOWS)
                return additional;
            else if (additional.startsWith(PATH_SEPARATOR))
                return additional;
            else
                return PATH_SEPARATOR + additional;
        }
        if (base.endsWith(PATH_SEPARATOR)) {
            if (additional.startsWith(PATH_SEPARATOR))
                return base + additional.substring(1);
            else
                return base + additional;
        }
        return base + PATH_SEPARATOR + additional;
    }

    /**
     * Gets the parent directory of a normalized path.
     *
     * @param file The path to get the parent of
     * @return The parent directory path
     */
    public static String normalizedPathParent(String file) {
        if (file.endsWith(PATH_SEPARATOR))
            file = file.substring(0, file.length() - 1);
        int index = file.lastIndexOf(PATH_SEPARATOR);
        if (index < 0)
            return "";
        return file.substring(0, index + 1);
    }
}
