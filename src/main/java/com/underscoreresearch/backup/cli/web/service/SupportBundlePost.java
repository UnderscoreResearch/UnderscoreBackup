package com.underscoreresearch.backup.cli.web.service;

import static com.underscoreresearch.backup.configuration.BackupModule.REPOSITORY_DB_PATH;
import static com.underscoreresearch.backup.configuration.CommandLineModule.KEY_FILE_NAME;
import static com.underscoreresearch.backup.configuration.CommandLineModule.LOG_FILE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.manifest.implementation.ShareManifestManagerImpl.SHARE_CONFIG_FILE;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_ACTIVATED_SHARE_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_ACTIVATED_SHARE_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;
import org.takes.rq.RqPrint;
import org.takes.rs.RsText;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.cli.ui.UIHandler;
import com.underscoreresearch.backup.cli.web.ExclusiveImplementation;
import com.underscoreresearch.backup.cli.web.JsonWrap;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupActivatedShare;
import com.underscoreresearch.backup.model.BackupConfiguration;

@Slf4j
public class SupportBundlePost extends JsonWrap {

    private static final ObjectReader READER = MAPPER.readerFor(GenerateSupportBundleRequest.class);
    private static final ObjectWriter WRITER = MAPPER.writerFor(GenerateSupportBundleResponse.class);

    public SupportBundlePost() {
        super(new Implementation());
    }

    @Data
    private static class GenerateSupportBundleRequest {
        private boolean includeLogs;
        private boolean includeConfig;
        private boolean includeMetadata;
        private boolean includeKey;
    }

    @Data
    @AllArgsConstructor
    private static class GenerateSupportBundleResponse {
        private String location;
    }

    private static class Implementation extends ExclusiveImplementation {
        private static boolean createSupportBundle(GenerateSupportBundleRequest request, File f) {
            try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(f))) {
                String manifestLocation = InstanceFactory.getInstance(MANIFEST_LOCATION);

                if (request.includeConfig) {
                    BackupConfiguration config = InstanceFactory.getInstance(BackupConfiguration.class);

                    addZipFile(out, "config.json", (str) -> BACKUP_CONFIGURATION_WRITER.writeValue(str, config.strippedCopy()));

                    processShareConfigs(out, manifestLocation);
                }

                if (request.includeLogs) {
                    String baseFileName;
                    try {
                        baseFileName = InstanceFactory.getInstance(LOG_FILE);
                        addZipFile(out, new File(baseFileName));
                        for (int i = 1; i < 9; i++) {
                            addZipFile(out, new File(baseFileName + "." + i + ".gz"));
                        }
                    } catch (Exception exc) {
                        // Can't find a log filename. I guess we don't want logs.
                        log.error("Failed to include all log files", exc);
                    }
                }

                if (request.includeMetadata) {
                    final MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
                    try (CloseableLock ignore = repository.acquireUpdateLock()) {
                        try (CloseableLock ignore2 = repository.acquireLock()) {
                            repository.close();
                            addZipFile(out, new File(InstanceFactory.getInstance(REPOSITORY_DB_PATH)));
                        }
                    }
                }

                if (request.includeKey) {
                    addZipFile(out, new File(InstanceFactory.getInstance(KEY_FILE_NAME)));
                }
                log.info("Generated support bundle at {}", f.getAbsolutePath());
                return true;
            } catch (Exception e) {
                log.error("Failed to generate support bundle", e);
                return false;
            }
        }

        private static void processShareConfigs(ZipOutputStream out, String manifestLocation) {
            File sharesDirectory = new File(manifestLocation, "shares");
            if (sharesDirectory.isDirectory()) {
                File[] files = sharesDirectory.listFiles();
                if (files != null) {
                    for (File shareFile : files) {
                        if (shareFile.isDirectory()) {
                            File configFile = new File(shareFile, SHARE_CONFIG_FILE);
                            if (configFile.exists()) {
                                try {
                                    BackupActivatedShare share = BACKUP_ACTIVATED_SHARE_READER.readValue(configFile);

                                    BackupActivatedShare strippedShare = BackupActivatedShare.builder()
                                            .share(share.getShare().toBuilder().destination(
                                                    share.getShare().getDestination()
                                                            .strippedDestination(null, null)).build())
                                            .usedDestinations(share.getUsedDestinations())
                                            .build();

                                    addZipFile(out, Path.of("shares", shareFile.getName(), SHARE_CONFIG_FILE).toString(),
                                            (str) -> BACKUP_ACTIVATED_SHARE_WRITER.writeValue(str, strippedShare));
                                } catch (IOException e) {
                                    log.error("Failed to read share definition for {}", shareFile.getName(), e);
                                }
                            }
                        }
                    }
                }
            }
        }

        private static void addZipFile(ZipOutputStream out, String filename, ContentWriter contentWriter) throws IOException {
            ZipEntry e = new ZipEntry(filename);
            out.putNextEntry(e);
            contentWriter.accept(new UnclosedStream(out));
            out.closeEntry();
        }

        private static void addZipFile(ZipOutputStream out, String filename, InputStream stream) throws IOException {
            addZipFile(out, filename, (str) -> IOUtils.copyStream(stream, str));
        }

        private static void addZipFile(ZipOutputStream out, File file, String name) throws IOException {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                if (files != null) {
                    for (File child : files) {
                        addZipFile(out, child, name + File.separator + child.getName());
                    }
                }
            } else if (file.isFile()) {
                try (FileInputStream stream = new FileInputStream(file)) {
                    addZipFile(out, name, stream);
                } catch (IOException exc) {
                    log.warn("Failed to add file to support bundle", exc);
                }
            }
        }

        private static void addZipFile(ZipOutputStream out, File file) throws IOException {
            addZipFile(out, file, file.getName());
        }

        @Override
        public Response actualAct(Request req) throws Exception {
            GenerateSupportBundleRequest request = READER.readValue(new RqPrint(req).printBody());

            Path path = Files.createTempDirectory("supportBundle");
            File f = File.createTempFile("supportBundle", ".zip", path.toFile());

            if (!createSupportBundle(request, f)) {
                return messageJson(400, "Failed to generate support bundle");
            }

            log.info("Support bundle generated at " + f.getAbsolutePath());

            UIHandler.openFolder(f.getParentFile());

            return new RsText(WRITER.writeValueAsString(new GenerateSupportBundleResponse(f.getAbsolutePath())));
        }

        @Override
        protected String getBusyMessage() {
            return "Generating support bundle";
        }

        private interface ContentWriter {
            void accept(OutputStream out) throws IOException;
        }

        private static class UnclosedStream extends OutputStream {
            private final OutputStream out;

            public UnclosedStream(OutputStream out) {
                this.out = out;
            }

            @Override
            public void write(int b) throws IOException {
                out.write(b);
            }

            @Override
            public void write(byte b[], int off, int len) throws IOException {
                out.write(b, off, len);
            }
        }
    }
}
