package com.underscoreresearch.backup.cli.web;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.ProvisionException;
import com.underscoreresearch.backup.cli.commands.VersionCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupFilter;
import com.underscoreresearch.backup.model.BackupFilterType;
import com.underscoreresearch.backup.model.BackupRetention;
import com.underscoreresearch.backup.model.BackupRetentionAdditional;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.model.BackupSetRoot;
import com.underscoreresearch.backup.model.BackupTimeUnit;
import com.underscoreresearch.backup.model.BackupTimespan;
import com.underscoreresearch.backup.service.api.model.ReleaseFileItem;
import com.underscoreresearch.backup.service.api.model.ReleaseResponse;
import com.underscoreresearch.backup.utils.state.MachineState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.SystemUtils;
import org.takes.Request;
import org.takes.Response;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static com.underscoreresearch.backup.cli.web.PingGet.getSiteUrl;
import static com.underscoreresearch.backup.cli.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.configuration.CommandLineModule.SERVICE_MODE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.SOURCE_CONFIG;
import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

@Slf4j
public class StateGet extends BaseWrap {
    private static final ObjectWriter WRITER = MAPPER.writerFor(StateResponse.class);

    public StateGet() {
        super(new Implementation());
    }

    @Data
    private static class NewVersion {
        private BigDecimal releaseDate;
        private String name;
        private String version;
        private String body;
        private String changeLog;
        private ReleaseFileItem download;

        private NewVersion(ReleaseResponse release) {
            releaseDate = release.getReleaseDate();
            name = release.getName();
            version = release.getVersion();
            body = release.getBody();
            changeLog = release.getChangeLog();
            download = InstanceFactory.getInstance(MachineState.class)
                    .getDistribution(release.getFiles());
        }

        public static NewVersion fromRelease(ReleaseResponse release) {
            if (release == null)
                return null;
            return new NewVersion(release);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    private static class StateResponse {
        private String pathSeparator;
        private String version;
        private String source;
        private String sourceName;
        private String siteUrl;
        private boolean validDestinations;
        private boolean serviceConnected;
        private boolean administrator;
        private String serviceSourceId;
        private boolean activeSubscription;
        private BackupSet defaultSet;
        private NewVersion newVersion;
        private String defaultRestoreFolder;
        private boolean repositoryReady;
    }

    private static class Implementation extends BaseImplementation {

        @Override
        public Response actualAct(Request req) throws Exception {
            try {
                String homeRoot;
                String defaultRestore;
                if (InstanceFactory.getInstance(SERVICE_MODE, Boolean.class)) {
                    if (SystemUtils.IS_OS_WINDOWS) {
                        homeRoot = "C:\\Users";
                        defaultRestore = "C:\\Restore";
                    } else if (SystemUtils.IS_OS_MAC_OSX) {
                        homeRoot = "/Users";
                        defaultRestore = "/Restore";
                    } else {
                        homeRoot = "/home";
                        String rootHome = System.getProperty("user.home");
                        defaultRestore = Paths.get(
                                rootHome,
                                "Restore").toString();
                    }
                } else {
                    homeRoot = System.getProperty("user.home");
                    defaultRestore = Paths.get(
                            homeRoot,
                            "Restore").toString();
                }
                String home = PathNormalizer.normalizePath(homeRoot);
                if (!home.endsWith(PATH_SEPARATOR)) {
                    home += PATH_SEPARATOR;
                }
                BackupConfiguration config = InstanceFactory.getInstance(BackupConfiguration.class);
                String manifestDestination = PathNormalizer.normalizePath(
                        InstanceFactory.getInstance(MANIFEST_LOCATION));
                List<BackupFilter> filters = new ArrayList<>();
                if (manifestDestination.startsWith(home)) {
                    String backupDir = manifestDestination.substring(home.length() + 1);
                    filters.add(BackupFilter.builder().type(BackupFilterType.EXCLUDE)
                            .paths(Lists.newArrayList(backupDir)).build());
                }

                boolean activeSubscription = false;
                boolean serviceConnected = false;
                String serviceSourceId = null;

                ServiceManager serviceManager = InstanceFactory.getInstance(ServiceManager.class);
                if (serviceManager.getToken() != null) {
                    serviceSourceId = serviceManager.getSourceId();
                    try {
                        activeSubscription = serviceManager.activeSubscription();

                        // This can get reset when checking subscription.
                        serviceConnected = serviceManager.getToken() != null;
                    } catch (Exception exc) {
                        log.warn("Failed to fetch subscription status", exc);
                    }
                }

                List<String> exclusions = Lists.newArrayList(
                        "(?i)\\.tmp$",
                        "(?i)\\.old$",
                        "(?i)\\.temp$",
                        "(?i)\\.part$",
                        "~$",
                        "(?i)\\.ost$",
                        "(?i)/te?mp/",
                        "/node_modules/",
                        "/npm-cache/",
                        "(?i)/\\.?caches?/",
                        "/\\.npm/",
                        "/\\#[^\\/]*\\#/",
                        "/\\.gradle/",
                        "/\\.cpan/",
                        "/build/");

                if (SystemUtils.IS_OS_WINDOWS || SystemUtils.IS_OS_MAC_OSX) {
                    exclusions.add("/OfficeFileCache/");
                }
                if (SystemUtils.IS_OS_WINDOWS) {
                    exclusions.add("/Google/Chrome/");
                    exclusions.add("/Microsoft/Edge/");
                    exclusions.add("/Microsoft/OneDrive/");
                    exclusions.add("/Mozilla/Firefox/");
                    exclusions.add("/\\$Recycle.Nin/");
                } else if (SystemUtils.IS_OS_MAC_OSX) {
                    exclusions.add("/lost\\+found/");
                } else {
                    exclusions.add("/lost\\+found/");
                    exclusions.add("/.config/google-chrome/");
                    exclusions.add("/.mozilla/firefox/");
                }

                BackupSetRoot root = BackupSetRoot.builder().filters(filters).build();
                root.setNormalizedPath(home);
                if (SystemUtils.IS_OS_WINDOWS) {
                    root.setFilters(Lists.newArrayList(BackupFilter
                            .builder()
                            .type(BackupFilterType.EXCLUDE)
                            .paths(Lists.newArrayList("OneDrive"))
                            .build()));
                }

                List<String> setDestinations = new ArrayList<>();
                if (config.getManifest() != null && config.getManifest().getDestination() != null) {
                    setDestinations.add(config.getManifest().getDestination());
                }

                BackupSet set = BackupSet.builder().schedule("0 3 * * *")
                        .exclusions(exclusions)
                        .roots(Lists.newArrayList(root))
                        .destinations(setDestinations)
                        .id("home")
                        .retention(BackupRetention.builder()
                                .defaultFrequency(BackupTimespan.builder().duration(1).unit(BackupTimeUnit.DAYS).build())
                                .older(Sets.newTreeSet(Lists.newArrayList(BackupRetentionAdditional.builder()
                                        .validAfter(BackupTimespan.builder().duration(1).unit(BackupTimeUnit.MONTHS).build())
                                        .frequency(BackupTimespan.builder().duration(1).unit(BackupTimeUnit.MONTHS).build())
                                        .build())))
                                .retainDeleted(BackupTimespan.builder().duration(1).unit(BackupTimeUnit.MONTHS).build()).build())
                        .build();

                boolean validDestinations = false;
                if (InstanceFactory.hasConfiguration(false)) {
                    BackupConfiguration sourceConfig = InstanceFactory.getInstance(SOURCE_CONFIG, BackupConfiguration.class);
                    if (sourceConfig != null) {
                        validDestinations = ConfigurationPost.isValidatesDestinations(sourceConfig);
                    }
                }

                boolean repositoryReady = false;
                if (validDestinations) {
                    try {
                        repositoryReady = InstanceFactory.getInstance(ManifestManager.class).isRepositoryReady();
                    } catch (ProvisionException ignored) {
                    }
                }

                String sourceName = InstanceFactory.getAdditionalSource() != null
                        ? InstanceFactory.getAdditionalSourceName()
                        : InstanceFactory.getInstance(ServiceManager.class).getSourceName();

                return encryptResponse(req, WRITER.writeValueAsString(StateResponse.builder()
                        .defaultSet(set)
                        .version(VersionCommand.getVersionEdition())
                        .validDestinations(validDestinations)
                        .serviceConnected(serviceConnected)
                        .activeSubscription(activeSubscription)
                        .serviceSourceId(serviceSourceId)
                        .administrator(InstanceFactory.getInstance(SERVICE_MODE, Boolean.class))
                        .sourceName(sourceName)
                        .siteUrl(getSiteUrl(null))
                        .source(InstanceFactory.getAdditionalSource())
                        .newVersion(NewVersion.fromRelease(serviceManager.newVersion()))
                        .pathSeparator(File.separator)
                        .repositoryReady(repositoryReady)
                        .defaultRestoreFolder(defaultRestore).build()));
            } catch (Exception exc) {
                log.warn("Failed to read existing config", exc);
            }
            return messageJson(404, "No valid config available");
        }
    }
}
