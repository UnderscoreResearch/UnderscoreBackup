package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.cli.commands.RebuildRepositoryCommand.downloadRemoteConfiguration;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;

import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.cli.web.ConfigurationPost;

@CommandPlugin(value = "download-config", description = "Download config from manifest destination",
        readonlyRepository = false)
@Slf4j
public class DownloadConfigCommand extends Command {

    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        try {
            String config = downloadRemoteConfiguration();
            ConfigurationPost.updateConfiguration(config, false);
            log.info("Successfully downloaded and replaced the configuration file");
        } catch (Exception exc) {
            log.error("Failed to download and replace config", exc);
        }
    }
}
