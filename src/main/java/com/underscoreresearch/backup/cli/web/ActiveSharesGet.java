package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rs.RsText;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.ShareManifestManager;

@Slf4j
public class ActiveSharesGet extends JsonWrap {
    private static ObjectWriter WRITER = MAPPER.writerFor(Shares.class);

    public ActiveSharesGet() {
        super(new Implementation());
    }

    @Data
    @AllArgsConstructor
    private static class Shares {
        private List<String> activeShares;
        private boolean shareEncryptionNeeded;
    }

    private static class Implementation implements Take {
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
                    return new RsText(WRITER.writeValueAsString(shares));
                } else {
                    return new RsText(WRITER.writeValueAsString(new Shares(new ArrayList<>(), false)));
                }
            } catch (Exception exc) {
                return messageJson(400, exc.getMessage());
            }
        }
    }
}
