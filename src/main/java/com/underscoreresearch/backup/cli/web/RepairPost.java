package com.underscoreresearch.backup.cli.web;

import com.google.common.base.Strings;
import com.underscoreresearch.backup.cli.commands.InteractiveCommand;
import com.underscoreresearch.backup.cli.ui.UIHandler;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import static com.underscoreresearch.backup.configuration.EncryptionModule.ROOT_KEY;
import static com.underscoreresearch.backup.manifest.implementation.ManifestManagerImpl.REPAIRING_REPOSITORY_OPERATION;

@Slf4j
public class RepairPost extends BaseWrap {

    public RepairPost() {
        super(new Implementation());
    }

    public static void executeAsyncOperation(Runnable runnable, BiConsumer<Thread, Boolean> shutdownHook, String taskName, String name) {
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean completed = new AtomicBoolean(false);
        Thread thread = new Thread(() -> {
            try {
                runnable.run();
            } catch (Exception exc) {
                log.error("Repository operation failed", exc);
            } finally {
                completed.set(true);
                while (!started.get()) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Interrupted while waiting for repair to complete", e);
                    }
                }
            }
            if (!InstanceFactory.isShutdown()) {
                new Thread(() -> {
                    InstanceFactory.reloadConfiguration(InstanceFactory.getAdditionalSource(),
                            InstanceFactory.getAdditionalSourceName(),
                            InteractiveCommand::startBackupIfAvailable);
                }, name).start();
            }
        },
                name);
        thread.setDaemon(true);

        InstanceFactory.addOrderedCleanupHook(() -> {
            InstanceFactory.shutdown();
            try {
                thread.join();
            } catch (InterruptedException e) {
                log.error("Failed to wait for operation to close", e);
            }
            shutdownHook.accept(thread, completed.get());
        });

        thread.start();
        while (!completed.get()) {
            if (taskName.equals(UIHandler.getActiveTaskMessage()))
                break;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Failed to wait", e);
            }
        }
        started.set(true);
    }

    public static void repairRepository(String password, boolean async) {
        log.info("Repairing local repository from logs");
        InstanceFactory.reloadConfiguration(InstanceFactory.getAdditionalSource(),
                InstanceFactory.getAdditionalSourceName(), null);

        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);

        LogConsumer logConsumer = InstanceFactory.getInstance(LogConsumer.class);

        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
        if (async) {
            executeAsyncOperation(() -> executeRepair(manifestManager, logConsumer, repository, password),
                    (thread, completed) -> {
                        try {
                            manifestManager.shutdown();
                        } catch (IOException e) {
                            log.error("Failed to shut down log replay", e);
                        }
                        if (!completed && manifestManager.isBusy()) {
                            try {
                                thread.join(1000);
                                if (!thread.isAlive())
                                    return;
                            } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                            }
                            log.info("Waiting for rebuild to get to a checkpoint");
                            do {
                                try {
                                    thread.join();
                                } catch (InterruptedException ignored) {
                                    Thread.currentThread().interrupt();
                                }
                            } while (thread.isAlive());
                        }
                    },
                    REPAIRING_REPOSITORY_OPERATION,
                    "RepositoryRepair");
        } else {
            executeRepair(manifestManager, logConsumer, repository, password);
        }
    }

    private static void executeRepair(ManifestManager manifestManager,
                                      LogConsumer logConsumer,
                                      MetadataRepository repository,
                                      String password) {
        try {
            manifestManager.setRepairingRepository(true);
            if (Strings.isNullOrEmpty(InstanceFactory.getAdditionalSource())) {
                manifestManager.initialize(logConsumer, true);
            }
            manifestManager.repairRepository(logConsumer, password);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            manifestManager.setRepairingRepository(false);
            try {
                repository.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class Implementation extends ExclusiveImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            String password = PrivateKeyRequest.decodePrivateKeyRequest(req);
            try {
                EncryptionIdentity publicKey = InstanceFactory.getInstance(ROOT_KEY, EncryptionIdentity.class);
                publicKey.getPrivateIdentity(password);
            } catch (Exception exc) {
                return messageJson(403, "Invalid password provided");
            }

            InstanceFactory.reloadConfiguration(
                    InstanceFactory.getAdditionalSource(),
                    InstanceFactory.getAdditionalSourceName(),
                    () -> repairRepository(password, true));
            return messageJson(200, "Repair started");
        }

        @Override
        protected String getBusyMessage() {
            return "Repairing local metadata";
        }
    }
}
