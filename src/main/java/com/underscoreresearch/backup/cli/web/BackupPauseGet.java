package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.ConfigurationPost.updateConfiguration;

import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupConfiguration;

@Slf4j
public class BackupPauseGet extends JsonWrap {
    private static final ObjectReader READER = new ObjectMapper()
            .readerFor(BackupConfiguration.class);
    private static final ObjectWriter WRITER = new ObjectMapper()
            .writerFor(BackupConfiguration.class);

    public BackupPauseGet() {
        super(new Implementation());
    }

    private static class Implementation implements Take {
        @Override
        public Response act(Request req) throws Exception {
            String config = WRITER.writeValueAsString(InstanceFactory.getInstance(BackupConfiguration.class));
            try {
                updateConfiguration(config, true, false);
                InstanceFactory.reloadConfiguration(null, null);
                return messageJson(200, "Updated configuration");
            } catch (Exception exc) {
                return messageJson(400, exc.getMessage());
            }
        }
    }
}
