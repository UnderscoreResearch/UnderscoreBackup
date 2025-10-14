package com.underscoreresearch.backup.ui.web.methods;

import com.fasterxml.jackson.databind.ObjectReader;
import com.underscoreresearch.backup.ui.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.RepositoryOpenMode;
import com.underscoreresearch.backup.model.BackupPendingSet;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.ui.web.PsAuthedContent.decodeRequestBody;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Web endpoint for restarting backup sets.
 * This class handles requests to restart backup sets by clearing their pending status.
 */
@Slf4j
public class RestartSetsPost extends BaseWrap {
    private static final ObjectReader READER = MAPPER.readerFor(RestartSetRequest.class);

    /**
     * Creates a new RestartSetsPost instance.
     */
    public RestartSetsPost() {
        super(new Implementation());
    }

    /**
     * Request class for restarting backup sets.
     * Contains the set IDs to restart.
     */
    @Data
    private static class RestartSetRequest {
        private Set<String> sets;
    }

    /**
     * Implementation class that handles backup set restart requests.
     */
    private static class Implementation extends ExclusiveImplementation {
        /**
         * Processes a request to restart backup sets.
         * Clears the pending status of the specified sets or all sets if none are specified.
         *
         * @param req The HTTP request
         * @return The HTTP response
         * @throws Exception If an error occurs during processing
         */
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

        /**
         * Gets the message to display when the system is busy.
         *
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Restarting sets";
        }
    }
}
