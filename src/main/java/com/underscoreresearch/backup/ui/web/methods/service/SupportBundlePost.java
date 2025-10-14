package com.underscoreresearch.backup.ui.web.methods.service;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.ui.desktop.UIHandler;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupActivatedShare;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.utils.log.LogUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;

import java.io.Closeable;
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

import static com.underscoreresearch.backup.ui.web.PsAuthedContent.decodeRequestBody;
import static com.underscoreresearch.backup.ui.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.configuration.BackupModule.REPOSITORY_DB_PATH;
import static com.underscoreresearch.backup.configuration.CommandLineModule.KEY_FILE_NAME;
import static com.underscoreresearch.backup.configuration.CommandLineModule.LOG_FILE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.manifest.implementation.ShareManifestManagerImpl.SHARE_CONFIG_FILE;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_ACTIVATED_SHARE_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_ACTIVATED_SHARE_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Endpoint for creating support bundles.
 * This class handles the creation of support bundles containing logs, configuration, and other
 * diagnostic information for troubleshooting purposes.
 */
@Slf4j
public class SupportBundlePost extends BaseWrap {

    private static final ObjectReader READER = MAPPER.readerFor(GenerateSupportBundleRequest.class);
    private static final ObjectWriter WRITER = MAPPER.writerFor(GenerateSupportBundleResponse.class);

    /**
     * Constructor for the SupportBundlePost endpoint.
     */
    public SupportBundlePost() {
        super(new Implementation());
    }

    /**
     * Data class for the generate support bundle request.
     */
    @Data
    private static class GenerateSupportBundleRequest {
        private boolean includeLogs;
        private boolean includeConfig;
        private boolean includeMetadata;
        private boolean includeKey;
    }

    /**
     * Data class for the generate support bundle response.
     */
    @Data
    @AllArgsConstructor
    private static class GenerateSupportBundleResponse {
        private String location;
    }

    /**
     * Implementation of the exclusive implementation that handles the HTTP request.
     */
    private static class Implementation extends ExclusiveImplementation {
        
        /**
         * Creates a support bundle with the requested components.
         * 
         * @param request The request specifying which components to include
         * @param f The file to write the support bundle to
         * @return True if the bundle was created successfully, false otherwise
         */
        private static boolean createSupportBundle(GenerateSupportBundleRequest request, File f) {
            try (Closeable ignore3 = UIHandler.registerTask("Generating support bundle", false)) {
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
                    log.info("Generated support bundle at \"{}\"", f.getAbsolutePath());
                    return true;
                }
            } catch (Exception e) {
                log.error("Failed to generate support bundle", e);
                return false;
            }
        }

        /**
         * Processes share configurations and adds them to the support bundle.
         * 
         * @param out The ZIP output stream
         * @param manifestLocation The location of the manifest files
         */
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
                                    log.error("Failed to read share definition for \"{}\"", shareFile.getName(), e);
                                }
                            }
                        }
                    }
                }
            }
        }

        /**
         * Adds a file to the ZIP archive using a content writer.
         * 
         * @param out The ZIP output stream
         * @param filename The name of the file in the archive
         * @param contentWriter The writer that generates the content
         * @throws IOException If there's an error writing to the archive
         */
        private static void addZipFile(ZipOutputStream out, String filename, ContentWriter contentWriter) throws IOException {
            ZipEntry e = new ZipEntry(filename);
            out.putNextEntry(e);
            contentWriter.accept(new UnclosedStream(out));
            out.closeEntry();
        }

        /**
         * Adds a file to the ZIP archive from an input stream.
         * 
         * @param out The ZIP output stream
         * @param filename The name of the file in the archive
         * @param stream The input stream to read from
         * @throws IOException If there's an error writing to the archive
         */
        private static void addZipFile(ZipOutputStream out, String filename, InputStream stream) throws IOException {
            addZipFile(out, filename, (str) -> IOUtils.copyStream(stream, str));
        }

        /**
         * Adds a file or directory to the ZIP archive.
         * 
         * @param out The ZIP output stream
         * @param file The file or directory to add
         * @param name The name of the file in the archive
         * @throws IOException If there's an error writing to the archive
         */
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

        /**
         * Adds a file to the ZIP archive.
         * 
         * @param out The ZIP output stream
         * @param file The file to add
         * @throws IOException If there's an error writing to the archive
         */
        private static void addZipFile(ZipOutputStream out, File file) throws IOException {
            addZipFile(out, file, file.getName());
        }

        /**
         * Processes the HTTP request and dumps stack traces before delegating to actualAct.
         * 
         * @param req The HTTP request
         * @return Response from actualAct
         * @throws Exception If there's an error processing the request
         */
        @Override
        public Response act(Request req) throws Exception {
            StringBuilder sb = new StringBuilder("Support bundle stack trace dump: ");
            LogUtil.dumpAllStackTrace(sb);
            log.info(sb.toString());

            return super.act(req);
        }

        /**
         * Processes the HTTP request to generate a support bundle.
         * 
         * @param req The HTTP request
         * @return Response containing the location of the generated bundle or an error message
         * @throws Exception If there's an error processing the request
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            GenerateSupportBundleRequest request = READER.readValue(decodeRequestBody(req));

            Path path = Files.createTempDirectory("supportBundle");
            File f = File.createTempFile("supportBundle", ".zip", path.toFile());

            if (!createSupportBundle(request, f)) {
                return messageJson(400, "Failed to generate support bundle");
            }

            log.info("Support bundle generated at \"" + f.getAbsolutePath() + "\"");

            UIHandler.openFolder(f.getParentFile());

            return encryptResponse(req, WRITER.writeValueAsString(new GenerateSupportBundleResponse(f.getAbsolutePath())));
        }

        /**
         * Returns the message to display when the system is busy.
         * 
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Generating support bundle";
        }

        /**
         * Interface for writing content to an output stream.
         */
        private interface ContentWriter {
            void accept(OutputStream out) throws IOException;
        }

        /**
         * Output stream wrapper that prevents closing the underlying stream.
         */
        private static class UnclosedStream extends OutputStream {
            private final OutputStream out;

            /**
             * Constructor for the UnclosedStream.
             * 
             * @param out The underlying output stream
             */
            public UnclosedStream(OutputStream out) {
                this.out = out;
            }

            /**
             * Writes a byte to the underlying stream.
             * 
             * @param b The byte to write
             * @throws IOException If there's an error writing to the stream
             */
            @Override
            public void write(int b) throws IOException {
                out.write(b);
            }

            /**
             * Writes a byte array to the underlying stream.
             * 
             * @param b The byte array to write
             * @param off The offset in the array
             * @param len The number of bytes to write
             * @throws IOException If there's an error writing to the stream
             */
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
            }
        }
    }
}
