package com.underscoreresearch.backup.ui.web;

import com.google.common.base.Strings;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupFile;
import lombok.Getter;
import org.takes.HttpException;
import org.takes.Request;
import org.takes.Response;
import org.takes.misc.Href;
import org.takes.rq.RqHref;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.underscoreresearch.backup.ui.web.BaseWrap.messageJson;
import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;

/**
 * Utility class for decoding destination and path information from requests.
 * This class provides methods to extract and validate paths and destinations from HTTP requests.
 */
@Getter
public final class DestinationDecoder {
    private Response response;
    private IOProvider provider;

    /**
     * Creates a new DestinationDecoder for the specified request.
     * Extracts and validates the destination parameter from the request.
     *
     * @param req The HTTP request
     * @throws IOException If there's an error processing the request
     */
    public DestinationDecoder(Request req) throws IOException {
        Href href = new RqHref.Base(req).href();
        String destination = null;
        for (String ts : href.param("destination")) {
            destination = ts;
        }
        if (Strings.isNullOrEmpty(destination)) {
            response = messageJson(400, "Missing destination parameter");
            return;
        }
        BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);
        BackupDestination backupDestination = configuration.getDestinations()
                .get(configuration.getManifest().getDestination());
        if (backupDestination == null) {
            response = messageJson(404, "Missing destination " + destination);
            return;
        }
        provider = IOProviderFactory.getProvider(backupDestination);
    }

    /**
     * Decodes a directory path from a request.
     * Ensures the path ends with a path separator.
     *
     * @param req The HTTP request
     * @param base The base path for the API
     * @return The decoded directory path
     * @throws IOException If there's an error decoding the path
     */
    public static String decodePath(Request req, String base) throws IOException {
        String path = decodeFile(req, base);
        if (!path.endsWith(PATH_SEPARATOR)) {
            path += PATH_SEPARATOR;
        }
        return path;
    }

    /**
     * Decodes a file path from a request.
     *
     * @param req The HTTP request
     * @param base The base path for the API
     * @return The decoded file path
     * @throws IOException If there's an error decoding the path
     * @throws HttpException If the path is invalid
     */
    public static String decodeFile(Request req, String base) throws IOException {
        Href href = new RqHref.Base(req).href();
        String path = href.path();
        if (!path.startsWith(base + PATH_SEPARATOR) || path.equals(base)) {
            throw new HttpException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "Invalid path specified"
            );
        }
        path = path.substring(base.length() + 1);
        path = URLDecoder.decode(path, StandardCharsets.UTF_8);
        return path;
    }

    /**
     * Gets the files requested in a request.
     * Extracts the path, timestamp, and deleted flag from the request and returns the matching files.
     *
     * @param req The HTTP request
     * @param base The base path for the API
     * @return The list of backup files matching the request
     * @throws IOException If there's an error processing the request
     */
    public static List<BackupFile> getRequestFiles(Request req, String base) throws IOException {
        String path = decodePath(req, base);
        Long timestamp = null;
        Href href = new RqHref.Base(req).href();
        for (String ts : href.param("timestamp")) {
            timestamp = Long.parseLong(ts);
        }

        boolean deleted = false;
        for (String val : href.param("include-deleted")) {
            if ("true".equals(val)) {
                deleted = true;
                break;
            }
        }

        BackupContentsAccess access = InstanceFactory.getInstance(ManifestManager.class)
                .backupContents(timestamp, deleted);

        List<BackupFile> ret = access.directoryFiles(path);
        return ret != null ? ret : new ArrayList<>();
    }
}
