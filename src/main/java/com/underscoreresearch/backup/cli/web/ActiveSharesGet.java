package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.util.ArrayList;
import java.util.List;

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
    }

    private static class Implementation implements Take {
        @Override
        public Response act(Request req) {
            try {
                if (InstanceFactory.hasConfiguration(false)) {
                    ManifestManager manager = InstanceFactory.getInstance(ManifestManager.class);
                    return new RsText(WRITER.writeValueAsString(new Shares(manager.getActivatedShares().keySet().stream()
                            .sorted().toList())));
                } else {
                    return new RsText(WRITER.writeValueAsString(new Shares(new ArrayList<>())));
                }
            } catch (Exception exc) {
                return messageJson(400, exc.getMessage());
            }
        }
    }
}
