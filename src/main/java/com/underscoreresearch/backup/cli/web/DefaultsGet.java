package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.SystemUtils;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rs.RsText;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.underscoreresearch.backup.cli.commands.VersionCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupFilter;
import com.underscoreresearch.backup.model.BackupFilterType;
import com.underscoreresearch.backup.model.BackupRetention;
import com.underscoreresearch.backup.model.BackupRetentionAdditional;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.model.BackupSetRoot;
import com.underscoreresearch.backup.model.BackupTimeUnit;
import com.underscoreresearch.backup.model.BackupTimespan;

@Slf4j
public class DefaultsGet extends JsonWrap {
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    private static class DefaultsResponse {
        private String defaultRestoreFolder;
        private String pathSeparator;
        private String version;
        private BackupSet set;
    }

    private static ObjectWriter WRITER = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .writerFor(DefaultsResponse.class);

    public DefaultsGet() {
        super(new Implementation());
    }

    private static class Implementation implements Take {
        @Override
        public Response act(Request req) throws Exception {
            try {
                String physicalHome = System.getProperty("user.home");
                String defaultRestore = Paths.get(
                        physicalHome,
                        "Restore").toString();
                String home = PathNormalizer.normalizePath(physicalHome);
                if (home.endsWith(PATH_SEPARATOR)) {
                    home = home.substring(0, home.length() - 1);
                }
                BackupConfiguration config = InstanceFactory.getInstance(BackupConfiguration.class);
                String manifestDestination = PathNormalizer.normalizePath(config.getManifest().getLocalLocation());
                List<BackupFilter> filters = new ArrayList<>();
                if (manifestDestination.startsWith(home)) {
                    String backupDir = manifestDestination.substring(home.length() + 1);
                    filters.add(BackupFilter.builder().type(BackupFilterType.EXCLUDE)
                            .paths(Lists.newArrayList(backupDir)).build());
                }

                List<String> exclusions = Lists.newArrayList(
                        "(?i)\\.tmp$",
                        "(?i)\\.old$",
                        "(?i)\\.temp$",
                        "~$",
                        "(?i)\\.ost$",
                        "(?i)/te?mp/",
                        "/node_modules/",
                        "/npm-cache/",
                        "/\\.cache/",
                        "/\\.npm/",
                        "/\\#[^\\/]*\\#/",
                        "/\\.gradle/",
                        "/\\.cpan/",
                        "/build/");

                if (!SystemUtils.IS_OS_WINDOWS) {
                    exclusions.add("/lost\\+found/");
                }

                BackupSetRoot root = BackupSetRoot.builder().filters(filters).build();
                root.setNormalizedPath(home);

                BackupSet set = BackupSet.builder().schedule("0 3 * * *")
                        .exclusions(exclusions)
                        .roots(Lists.newArrayList(root))
                        .destinations(Lists.newArrayList(config.getManifest().getDestination()))
                        .id("home")
                        .retention(BackupRetention.builder()
                                .defaultFrequency(BackupTimespan.builder().duration(1).unit(BackupTimeUnit.DAYS).build())
                                .older(Sets.newTreeSet(Lists.newArrayList(BackupRetentionAdditional.builder()
                                        .validAfter(BackupTimespan.builder().duration(1).unit(BackupTimeUnit.MONTHS).build())
                                        .frequency(BackupTimespan.builder().duration(1).unit(BackupTimeUnit.MONTHS).build())
                                        .build())))
                                .retainDeleted(BackupTimespan.builder().duration(1).unit(BackupTimeUnit.MONTHS).build()).build())
                        .build();
                return new RsText(WRITER.writeValueAsString(DefaultsResponse.builder()
                        .set(set)
                        .version(VersionCommand.getVersion())
                        .pathSeparator(File.separator)
                        .defaultRestoreFolder(defaultRestore).build()));
            } catch (Exception exc) {
                log.warn("Failed to read existing config", exc);
            }
            return messageJson(404, "No valid config available");
        }
    }
}
