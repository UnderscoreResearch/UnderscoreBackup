package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.configuration.CommandLineModule.CONFIG_FILE_LOCATION;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.SystemUtils;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rq.RqPrint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.cli.ConfigurationValidator;
import com.underscoreresearch.backup.cli.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupConfiguration;

@Slf4j
public class ConfigurationPost extends JsonWrap {
    private static final ObjectReader READER = new ObjectMapper()
            .readerFor(BackupConfiguration.class);
    private static final ObjectWriter WRITER = new ObjectMapper()
            .writerFor(BackupConfiguration.class);

    public ConfigurationPost() {
        super(new Implementation());
    }

    private static class Implementation implements Take {
        @Override
        public Response act(Request req) throws Exception {
            String config = new RqPrint(req).printBody();
            try {
                updateConfiguration(config, false);
                InstanceFactory.reloadConfiguration(null);
                new Thread(() -> {
                    try {
                        InteractiveCommand.startBackupIfAvailable();
                    } catch (Exception e) {
                        log.error("Failed to start backup", e);
                    }
                }, "ConfigurationPointStartBackup").start();
                return messageJson(200, "Updated configuration");
            } catch (Exception exc) {
                return messageJson(400, exc.getMessage());
            }
        }
    }

    public static void updateConfiguration(String config, boolean clearInteractiveBackup) throws IOException {
        BackupConfiguration configuration = READER.readValue(config);
        if (clearInteractiveBackup) {
            configuration.getManifest().setInteractiveBackup(null);
            config = WRITER.writeValueAsString(configuration);
        }
        ConfigurationValidator.validateConfiguration(configuration, false);
        File file = new File(InstanceFactory.getInstance(CONFIG_FILE_LOCATION));
        try (OutputStreamWriter writer = new FileWriter(file)) {
            writer.write(config);
        }

        setReadOnlyFilePermissions(file);
    }

    public static void setReadOnlyFilePermissions(File file) throws IOException {
        if (!SystemUtils.IS_OS_WINDOWS) {
            HashSet<PosixFilePermission> set = new HashSet<PosixFilePermission>();

            set.add(PosixFilePermission.OWNER_READ);
            set.add(PosixFilePermission.OWNER_WRITE);

            Files.setPosixFilePermissions(file.toPath(), set);
        }
    }
}
