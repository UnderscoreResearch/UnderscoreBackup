package com.underscoreresearch.backup.cli.web;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rq.RqPrint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.underscoreresearch.backup.cli.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;

@Slf4j
public class RestartSetsPost extends JsonWrap {
    @Data
    private static class RestartSetRequest {
        private Set<String> sets;
    }

    private static final ObjectReader READER = new ObjectMapper()
            .readerFor(RestartSetRequest.class);

    public RestartSetsPost() {
        super(new Implementation());
    }

    private static class Implementation implements Take {
        @Override
        public Response act(Request req) throws Exception {
            String body = new RqPrint(req).printBody();
            try {
                RestartSetRequest request = READER.readValue(body);
                MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
                repository.open(false);
                Set<String> sets;
                if (request.sets == null) {
                    sets = repository.getPendingSets().stream().map(set -> set.getSetId())
                            .filter(id -> !id.equals("") && !id.equals("=")).collect(Collectors.toSet());
                } else {
                    sets = request.getSets();
                }
                sets.forEach(set -> {
                    try {
                        repository.deletePendingSets(set);
                    } catch (IOException e) {
                        log.error("Failed to reset pending schedule for set {}", set);
                    }
                });
                InstanceFactory.reloadConfiguration(null, null,
                        () -> InteractiveCommand.startBackupIfAvailable());
                return messageJson(200, "Updated configuration");
            } catch (Exception exc) {
                log.error("Failed to reset sets", exc);
                return messageJson(400, exc.getMessage());
            }
        }
    }
}
