package com.underscoreresearch.backup.cli.web;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;

import com.underscoreresearch.backup.cli.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;

@Slf4j
public class ActivateSharesPost extends JsonWrap {
    public ActivateSharesPost() {
        super(new Implementation());
    }

    public static void startAsyncManagerOperation(ManifestManager manager, Runnable runnable, String name) {
        AtomicBoolean completed = new AtomicBoolean();
        Thread thread = new Thread(() -> {
            try {
                runnable.run();
            } catch (Exception e) {
                log.error("Failed to activate shares", e);
            } finally {
                completed.set(true);
                InstanceFactory.reloadConfiguration(InteractiveCommand::startBackupIfAvailable);
            }
        }, name);

        thread.start();

        // Wait for thread to start before we return the call
        while (thread.isAlive() && !manager.isBusy() && !completed.get()) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                log.warn("Failed to wait", e);
            }
        }
    }

    private static class Implementation extends ExclusiveImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            try {
                String password = PrivateKeyRequest.decodePrivateKeyRequest(req);
                if (!PrivateKeyRequest.validatePassword(password)) {
                    return messageJson(403, "Invalid password provided");
                }
                EncryptionKey.PrivateKey privateKey = InstanceFactory.getInstance(EncryptionKey.class)
                        .getPrivateKey(password);

                InstanceFactory.reloadConfiguration(() -> {
                    ManifestManager manager = InstanceFactory.getInstance(ManifestManager.class);
                    InstanceFactory.addOrderedCleanupHook(() -> {
                        try {
                            manager.shutdown();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });

                    startAsyncManagerOperation(manager, () -> {
                        try {
                            manager.activateShares(InstanceFactory.getInstance(LogConsumer.class),
                                    privateKey);
                        } catch (IOException exc) {
                            throw new RuntimeException(exc);
                        }
                    }, "ActivatingShares");
                });

                return messageJson(200, "Started share activation");
            } catch (Exception exc) {
                return messageJson(400, exc.getMessage());
            }
        }

        @Override
        protected String getBusyMessage() {
            return "Activating shares";
        }
    }
}
