package com.underscoreresearch.backup.cli.web;

import com.fasterxml.jackson.databind.ObjectReader;
import com.underscoreresearch.backup.cli.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.RepositoryOpenMode;
import com.underscoreresearch.backup.model.BackupPendingSet;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.cli.web.PsAuthedContent.decodeRequestBody;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

@Slf4j
public class RestartSetsPost extends BaseWrap {
    private static final ObjectReader READER = MAPPER.readerFor(RestartSetRequest.class);

    public RestartSetsPost() {
        super(new Implementation());
    }

    @Data
    private static class RestartSetRequest {
        private Set<String> sets;
    }

    private static class Implementation extends ExclusiveImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            String body = decodeRequestBody(req);
            try {
                RestartSetRequest request = READER.readValue(body);
                MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
                repository.open(RepositoryOpenMode.READ_WRITE);
                Set<String> sets;
                if (request.sets == null) {
                    sets = repository.getPendingSets().stream().map(BackupPendingSet::getSetId)
                            .filter(id -> !id.isEmpty() && !id.equals("=")).collect(Collectors.toSet());
                } else {
                    sets = request.getSets();
                }
                sets.forEach(set -> {
                    try {
                        repository.deletePendingSets(set);
                    } catch (IOException e) {
                        log.error("Failed to reset pending schedule for set \"{}\"", set);
                    }
                });
                InstanceFactory.reloadConfiguration(
                        InteractiveCommand::startBackupIfAvailable);
                return messageJson(200, "Updated configuration");
            } catch (Exception exc) {
                log.error("Failed to reset sets", exc);
                return messageJson(400, exc.getMessage());
            }
        }

        @Override
        protected String getBusyMessage() {
            return "Restarting sets";
        }
    }
}
