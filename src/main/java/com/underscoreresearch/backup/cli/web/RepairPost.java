package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.configuration.EncryptionModule.ROOT_KEY;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;

import com.google.common.base.Strings;
import com.underscoreresearch.backup.cli.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;

@Slf4j
public class RepairPost extends BaseWrap {

    public RepairPost() {
        super(new Implementation());
    }

    public static void repairRepository(String password, boolean async) {
        log.info("Repairing local repository from logs");
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);

        LogConsumer logConsumer = InstanceFactory.getInstance(LogConsumer.class);

        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
        if (async) {
            AtomicBoolean started = new AtomicBoolean(false);
            Thread thread = new Thread(() -> {
                try {
                    executeRepair(manifestManager, logConsumer, repository, password);
                } catch (Exception exc) {
                    log.error("Failed to repair repository", exc);
                } finally {
                    if (started.get())
                        InstanceFactory.reloadConfiguration(InstanceFactory.getAdditionalSource(),
                                InstanceFactory.getAdditionalSourceName(),
                                InteractiveCommand::startBackupIfAvailable);
                }
            },
                    "RepositoryRepair");
            thread.setDaemon(true);

            InstanceFactory.addOrderedCleanupHook(() -> {
                try {
                    manifestManager.shutdown();
                } catch (IOException e) {
                    log.error("Failed to shut down log replay", e);
                }
                if (manifestManager.isBusy()) {
                    try {
                        thread.join(1000);
                        if (!thread.isAlive())
                            return;
                    } catch (InterruptedException ignored) {
                    }
                    log.info("Waiting for rebuild to get to a checkpoint");
                    do {
                        try {
                            thread.join();
                        } catch (InterruptedException ignored) {
                        }
                    } while (thread.isAlive());
                }
            });

            thread.start();
            while (!manifestManager.isBusy() && thread.isAlive()) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    log.error("Failed to wait", e);
                }
            }
            started.set(true);
            if (!thread.isAlive()) {
                InstanceFactory.reloadConfiguration(InstanceFactory.getAdditionalSource(),
                        InstanceFactory.getAdditionalSourceName(),
                        InteractiveCommand::startBackupIfAvailable);
            }
        } else {
            executeRepair(manifestManager, logConsumer, repository, password);
        }
    }

    private static void executeRepair(ManifestManager manifestManager,
                                      LogConsumer logConsumer,
                                      MetadataRepository repository,
                                      String password) {
        try {
            if (Strings.isNullOrEmpty(InstanceFactory.getAdditionalSource())) {
                manifestManager.initialize(logConsumer, true);
            }
            manifestManager.repairRepository(logConsumer, password);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
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
                EncryptionKey publicKey = InstanceFactory.getInstance(ROOT_KEY, EncryptionKey.class);
                publicKey.getPrivateKey(password);
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
