package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.utils.LogUtil.debug;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

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
        needPrivateKey = false, readonlyRepository = false, preferNice = true)
@Slf4j
public class BackupCommand extends SimpleCommand {
    public static void executeBackup(boolean asynchronous) throws Exception {
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
        ScannerScheduler scheduler = InstanceFactory.getInstance(ScannerScheduler.class);
        UploadScheduler uploadScheduler = InstanceFactory.getInstance(UploadScheduler.class);
        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
        try {
            IOUtils.waitForInternet(() -> {
                manifestManager.validateIdentity();
                return null;
            });
        } catch (Exception exc) {
            if (exc.getCause() instanceof ParseException)
                log.error(exc.getCause().getMessage());
            else {
                log.error("Failed to start backup: " + exc.getMessage(), exc);
            }
            return;
        }

        AtomicBoolean started = new AtomicBoolean(false);

        InstanceFactory.addOrderedCleanupHook(() -> {
            started.set(false);
            debug(() -> log.debug("Backup shutdown initiated"));

            InstanceFactory.shutdown();
            scheduler.shutdown();
            uploadScheduler.shutdown();
            scheduler.waitForCompletion();
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
            Thread thread = new Thread(() -> executeScheduler(started, scheduler, manifestManager), "ScannerScheduler");
            thread.start();
            while (!started.get() && thread.isAlive()) {
                Thread.sleep(1);
            }
        } else {
            executeScheduler(started, scheduler, manifestManager);
        }
    }

    private static void executeScheduler(AtomicBoolean started, ScannerScheduler scheduler, ManifestManager manifestManager) {
        try {
            started.set(true);
            IOUtils.waitForInternet(() -> {
                manifestManager.initialize(InstanceFactory.getInstance(LogConsumer.class), true);
                return null;
            });

            if (started.get()) {
                scheduler.start();
                debug(() -> log.debug("Backup scheduler shutdown"));
            }
        } catch (Exception e) {
            log.error("Failed to initialize manifest", e);
        }
    }

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
}
