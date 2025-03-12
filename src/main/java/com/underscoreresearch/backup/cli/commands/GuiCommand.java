package com.underscoreresearch.backup.cli.commands;

import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.cli.ui.AwtFileUIManager;
import com.underscoreresearch.backup.cli.ui.UIHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;

import java.awt.SystemTray;

@CommandPlugin(value = "gui", description = "Show GUI for service process",
        needConfiguration = false, needPrivateKey = false, readonlyRepository = true,
        preferNice = true)
@Slf4j
public class GuiCommand extends Command {
    public void executeCommand(CommandLine commandLine) throws Exception {
        if (SystemTray.isSupported()) {
            UIHandler.setup(new AwtFileUIManager());

            Thread.sleep(Integer.MAX_VALUE);
        } else {
            log.warn("System tray is not supported for GUI process, exiting");
        }
    }
}
