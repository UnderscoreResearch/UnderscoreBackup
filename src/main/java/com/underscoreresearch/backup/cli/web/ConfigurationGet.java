package com.underscoreresearch.backup.cli.web;

import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rs.RsText;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupConfiguration;

@Slf4j
public class ConfigurationGet extends JsonWrap {
    private static ObjectWriter WRITER = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .writerFor(BackupConfiguration.class);

    public ConfigurationGet() {
        super(new Implementation());
    }

    private static class Implementation implements Take {
        @Override
        public Response act(Request req) throws Exception {
            try {
                if (InstanceFactory.hasConfiguration(false)) {
                    BackupConfiguration config = InstanceFactory.getInstance(CommandLineModule.SOURCE_CONFIG,
                            BackupConfiguration.class);
                    return new RsText(WRITER.writeValueAsString(config));
                }
            } catch (Exception exc) {
                log.warn("Failed to read existing config", exc);
            }
            return messageJson(404, "No valid config available");
        }
    }
}
