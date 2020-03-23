package com.underscoreresearch.backup.cli.commands;

import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.ScannerScheduler;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.io.UploadScheduler;
import com.underscoreresearch.backup.manifest.ManifestManager;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

import static com.underscoreresearch.backup.utils.LogUtil.debug;

@CommandPlugin(value = "backup", description = "Run backup operation continuously",
        needPrivateKey = false, readonlyRepository = false)
@Slf4j
public class BackupCommand extends SimpleCommand {
    public void executeCommand() throws Exception {
        ScannerScheduler scheduler = InstanceFactory.getInstance(ScannerScheduler.class);
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
        UploadScheduler uploadScheduler = InstanceFactory.getInstance(UploadScheduler.class);
        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);

        IOUtils.waitForInternet(() -> {
            manifestManager.initialize();
            return null;
        });

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                debug(() -> log.debug("Shutdown initiated"));

                InstanceFactory.shutdown(true);
                scheduler.shutdown();
                uploadScheduler.shutdown();

                synchronized (scheduler) {

                    try {
                        repository.flushLogging();
                        manifestManager.shutdown();
                        repository.close();
                    } catch (IOException e) {
                        log.error("Failed to close manifest", e);
                    }
                }
                log.info("Backup shutdown completed");
            }
        });

        synchronized (scheduler) {
            scheduler.start();
            debug(() -> log.debug("Scheduler shut down"));
        }
    }
}
