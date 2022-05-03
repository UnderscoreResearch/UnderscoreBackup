package com.underscoreresearch.backup.cli.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.cli.ConfigurationValidator;
import com.underscoreresearch.backup.cli.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.SystemUtils;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rq.RqPrint;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;

import static com.underscoreresearch.backup.cli.web.ConfigurationPost.updateConfiguration;
import static com.underscoreresearch.backup.configuration.CommandLineModule.CONFIG_FILE_LOCATION;

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
                updateConfiguration(config, true);
                InstanceFactory.reloadConfiguration(null);
                return messageJson(200, "Updated configuration");
            } catch (Exception exc) {
                return messageJson(400, exc.getMessage());
            }
        }
    }
}
