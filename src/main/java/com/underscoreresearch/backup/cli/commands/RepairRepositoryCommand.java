package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.cli.commands.ConfigureCommand.reloadIfRunning;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;

import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.cli.web.RepairPost;

@CommandPlugin(value = "repair-repository", description = "Repair repository metadata from logs",
        readonlyRepository = false, supportSource = true)
@Slf4j
public class RepairRepositoryCommand extends Command {
    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        RepairPost.repairRepository(getPassword(), false);

        reloadIfRunning();
    }
}
