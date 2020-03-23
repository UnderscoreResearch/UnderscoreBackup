package com.underscoreresearch.backup.cli.commands;

import com.underscoreresearch.backup.cli.Command;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

public abstract class SimpleCommand extends Command {
    public void executeCommand(CommandLine commandLine) throws Exception {
        if (commandLine.getArgList().size() != 1) {
            throw new ParseException("Too many arguments for command");
        }
        executeCommand();
    }

    protected abstract void executeCommand() throws Exception;
}
