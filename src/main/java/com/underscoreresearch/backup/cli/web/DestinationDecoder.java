package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.JsonWrap.messageJson;
import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

import org.takes.HttpException;
import org.takes.Request;
import org.takes.Response;
import org.takes.misc.Href;
import org.takes.rq.RqHref;

import com.google.common.base.Strings;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.manifest.implementation.BackupContentsAccessImpl;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupFile;

@Getter
public final class DestinationDecoder {
    private Response response;
    private String path;
    private IOProvider provider;

    public DestinationDecoder(Request req, String base) throws IOException {
        path = decodePath(req, base);
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

    public static String decodePath(Request req, String base) throws IOException {
        Href href = new RqHref.Base(req).href();
        String path = href.path();
        if (!path.startsWith(base + PATH_SEPARATOR) || path.equals(base)) {
            throw new HttpException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "Invalid path specified"
            );
        }
        path = path.substring(base.length() + 1);
        path = URLDecoder.decode(path, "UTF-8");
        if (!path.endsWith(PATH_SEPARATOR)) {
            path += PATH_SEPARATOR;
        }
        return path;
    }

    public static List<BackupFile> getRequestFiles(Request req, String base) throws IOException {
        String path = decodePath(req, base);
        Long timestamp = null;
        Href href = new RqHref.Base(req).href();
        for (String ts : href.param("timestamp")) {
            timestamp = Long.parseLong(ts);
        }

        BackupContentsAccess access = new BackupContentsAccessImpl(
                InstanceFactory.getInstance(MetadataRepository.class), timestamp, false);

        List<BackupFile> ret = access.directoryFiles(path);
        return ret != null ? ret : new ArrayList<>();
    }
}
