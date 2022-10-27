package com.underscoreresearch.backup.cli.web;

import java.io.IOException;

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

    private static class Implementation extends BaseImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            try {
                String password = PrivateKeyRequest.decodePrivateKeyRequest(req);
                if (!PrivateKeyRequest.validatePassphrase(password)) {
                    return messageJson(403, "Invalid passphrase provided");
                }
                EncryptionKey.PrivateKey privateKey = InstanceFactory.getInstance(EncryptionKey.class)
                        .getPrivateKey(password);

                InstanceFactory.reloadConfiguration(null, () -> {
                    ManifestManager manager = InstanceFactory.getInstance(ManifestManager.class);
                    InstanceFactory.addOrderedCleanupHook(() -> {
                        try {
                            manager.shutdown();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });

                    Thread thread = new Thread(() -> {
                        try {
                            manager.activateShares(InstanceFactory.getInstance(LogConsumer.class),
                                    privateKey);
                        } catch (Exception e) {
                            log.error("Failed to activate shares", e);
                        } finally {
                            InstanceFactory.reloadConfiguration(
                                    null,
                                    () -> InteractiveCommand.startBackupIfAvailable());
                        }
                    }, "ActivatingShares");

                    thread.start();

                    // Wait for thread to start before we return the call
                    while (thread.isAlive() && !manager.isBusy()) {
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            log.warn("Failed to wait", e);
                        }
                    }
                });

                return messageJson(200, "Started share activation");
            } catch (Exception exc) {
                return messageJson(400, exc.getMessage());
            }
        }
    }
}
