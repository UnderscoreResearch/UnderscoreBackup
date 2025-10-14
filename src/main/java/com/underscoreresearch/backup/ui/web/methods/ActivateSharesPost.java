package com.underscoreresearch.backup.ui.web.methods;

import com.underscoreresearch.backup.ui.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import com.underscoreresearch.backup.ui.web.PrivateKeyRequest;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.underscoreresearch.backup.ui.web.methods.service.CreateSecretPut.encryptionIdentity;

/**
 * Web endpoint for activating shared backup repositories.
 * This class handles the activation of shared backup repositories by validating
 * the provided password and initiating the share activation process.
 */
@Slf4j
public class ActivateSharesPost extends BaseWrap {
    /**
     * Creates a new ActivateSharesPost instance.
     */
    public ActivateSharesPost() {
        super(new Implementation());
    }

    /**
     * Starts an asynchronous operation on the manifest manager.
     * This method creates a background thread to execute the operation and waits for it to start.
     *
     * @param manager The manifest manager to operate on
     * @param runnable The operation to execute
     * @param name The name of the thread
     */
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

    /**
     * Implementation class that handles the actual share activation process.
     */
    private static class Implementation extends ExclusiveImplementation {
        /**
         * Processes the share activation request.
         * Validates the password, gets the private identity, and starts the share activation process.
         *
         * @param req The HTTP request
         * @return The HTTP response
         * @throws Exception If an error occurs during processing
         */
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

        /**
         * Gets the message to display when the system is busy.
         *
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Activating shares";
        }
    }
}
