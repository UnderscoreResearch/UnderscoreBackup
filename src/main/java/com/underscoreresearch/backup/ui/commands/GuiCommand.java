package com.underscoreresearch.backup.ui.commands;

import com.underscoreresearch.backup.ui.desktop.AwtFileUIManager;
import com.underscoreresearch.backup.ui.desktop.UIHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;

import java.awt.SystemTray;

/**
 * Command that launches the graphical user interface for the backup service.
 * This command sets up a system tray icon and UI handlers for user interaction
 * with the backup service.
 */
@CommandPlugin(value = "gui", description = "Show GUI for service process",
        needConfiguration = false, needPrivateKey = false, readonlyRepository = true,
        preferNice = true)
@Slf4j
public class GuiCommand extends Command {
    /**
     * Executes the command to launch the GUI interface.
     * If the system tray is supported, it sets up the UI handler and keeps
     * the process running. Otherwise, it logs a warning and exits.
     *
     * @param commandLine The parsed command line arguments
     * @throws Exception If an error occurs during GUI setup
     */
    public void executeCommand(CommandLine commandLine) throws Exception {
        if (SystemTray.isSupported()) {
            UIHandler.setup(new AwtFileUIManager());

            Thread.sleep(Integer.MAX_VALUE);
        } else {
            log.warn("System tray is not supported for GUI process, exiting");
        }
    }
}
