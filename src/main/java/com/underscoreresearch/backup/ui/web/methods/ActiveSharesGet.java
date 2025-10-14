package com.underscoreresearch.backup.ui.web.methods;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.ShareManifestManager;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.underscoreresearch.backup.ui.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Web endpoint for retrieving information about active shares.
 * This class provides information about currently activated shares in the backup system.
 */
@Slf4j
public class ActiveSharesGet extends BaseWrap {
    private static final ObjectWriter WRITER = MAPPER.writerFor(Shares.class);

    /**
     * Creates a new ActiveSharesGet instance.
     */
    public ActiveSharesGet() {
        super(new Implementation());
    }

    /**
     * Data class representing share information.
     */
    @Data
    @AllArgsConstructor
    private static class Shares {
        private List<String> activeShares;
        private boolean shareEncryptionNeeded;
    }

    /**
     * Implementation class that handles retrieving active share information.
     */
    private static class Implementation implements Take {
        /**
         * Processes the request to get active shares.
         * Retrieves information about active shares from the manifest manager.
         *
         * @param req The HTTP request
         * @return The HTTP response containing share information
         */
        @Override
        public Response act(Request req) {
            try {
                if (InstanceFactory.hasConfiguration(false)) {
                    ManifestManager manager = InstanceFactory.getInstance(ManifestManager.class);
                    Map<String, ShareManifestManager> activatedShares = manager.getActivatedShares();
                    boolean needEncryption = activatedShares.values().stream().anyMatch(shareManager ->
                            !shareManager.getActivatedShare().isUpdatedEncryption());
                    Shares shares = new Shares(activatedShares.keySet().stream().sorted().toList(),
                            needEncryption);
                    return encryptResponse(req, WRITER.writeValueAsString(shares));
                } else {
                    return encryptResponse(req, WRITER.writeValueAsString(new Shares(new ArrayList<>(), false)));
                }
            } catch (Throwable exc) {
                log.error("Failed to get active shares", exc);
                return messageJson(500, exc.getMessage());
            }
        }
    }
}
