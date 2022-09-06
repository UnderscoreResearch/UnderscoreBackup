package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.utils.LogUtil.debug;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.ParseException;

import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.ScannerScheduler;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.io.UploadScheduler;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.utils.StateLogger;

@CommandPlugin(value = "backup", description = "Run backup operation continuously",
        needPrivateKey = false, readonlyRepository = false)
@Slf4j
public class BackupCommand extends SimpleCommand {
    public void executeCommand() throws Exception {
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
        repository.getPendingSets().stream()
                .filter(pendingSet -> pendingSet.getScheduledAt() == null && !pendingSet.getSetId().equals("") && !pendingSet.getSetId().equals("="))
                .forEach(pendingSet -> {
                    try {
                        repository.deletePendingSets(pendingSet.getSetId());
                    } catch (IOException e) {
                        log.error("Failed to remove completed non recurring backup set", e);
                    }
                });

        executeBackup(false);
    }

    public static void executeBackup(boolean asynchronous) throws Exception {
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
        ScannerScheduler scheduler = InstanceFactory.getInstance(ScannerScheduler.class);
        UploadScheduler uploadScheduler = InstanceFactory.getInstance(UploadScheduler.class);
        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
        try {
            manifestManager.validateIdentity();
        } catch (Exception exc) {
            if (exc.getCause() instanceof ParseException)
                log.error(exc.getCause().getMessage());
            else {
                log.error("Failed to start backup", exc);
            }
            return;
        }

        IOUtils.waitForInternet(() -> {
            manifestManager.initialize(InstanceFactory.getInstance(LogConsumer.class), false);
            return null;
        });

        InstanceFactory.addOrderedCleanupHook(() -> {
            debug(() -> log.debug("Backup shutdown initiated"));

            InstanceFactory.shutdown();
            scheduler.shutdown();
            uploadScheduler.shutdown();
            InstanceFactory.getInstance(StateLogger.class).reset();

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
        });

        if (asynchronous) {
            Thread thread = new Thread(() -> executeScheduler(scheduler));
            thread.start();
            do {
                Thread.sleep(1);
            } while(!scheduler.isRunning() && thread.isAlive());
        } else {
            executeScheduler(scheduler);
        }
    }

    private static void executeScheduler(ScannerScheduler scheduler) {
        synchronized (scheduler) {
            scheduler.start();
            debug(() -> log.debug("Backup scheduler shutdown"));
        }
    }
}
