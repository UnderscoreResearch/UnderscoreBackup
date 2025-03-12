package com.underscoreresearch.backup.cli.web;

import com.underscoreresearch.backup.cli.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.underscoreresearch.backup.cli.web.service.CreateSecretPut.encryptionIdentity;

@Slf4j
public class ActivateSharesPost extends BaseWrap {
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
        thread.setDaemon(true);
        thread.start();

        // Wait for thread to start before we return the call
        while (thread.isAlive() && !manager.isBusy() && !completed.get()) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Failed to wait", e);
            }
        }
    }

    private static class Implementation extends ExclusiveImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            String password = PrivateKeyRequest.decodePrivateKeyRequest(req);
            try {
                if (!PrivateKeyRequest.validatePassword(password)) {
                    return messageJson(403, "Invalid password provided");
                }
                EncryptionIdentity.PrivateIdentity privateIdentity = encryptionIdentity().getPrivateIdentity(password);

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
                                    privateIdentity);
                        } catch (IOException exc) {
                            throw new RuntimeException(exc);
                        }
                    }, "ActivatingShares");
                });

                return messageJson(200, "Started share activation");
            } catch (Exception exc) {
                log.warn("Failed to activate shares", exc);
                return messageJson(400, exc.getMessage());
            }
        }

        @Override
        protected String getBusyMessage() {
            return "Activating shares";
        }
    }
}
