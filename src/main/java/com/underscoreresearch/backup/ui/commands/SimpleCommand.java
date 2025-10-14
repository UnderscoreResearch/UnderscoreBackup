package com.underscoreresearch.backup.ui.commands;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

/**
 * Base class for simple commands that don't require additional command-line arguments.
 * This class provides common validation and execution logic for simple commands.
 */
public abstract class SimpleCommand extends Command {
    /**
     * Validates that no additional file arguments are provided to the command.
     *
     * @param commandLine The parsed command line
     * @throws ParseException If additional arguments are found
     */
    public static void validateNoFiles(CommandLine commandLine) throws ParseException {
        if (commandLine.getArgList().size() > 1) {
            throw new ParseException("Too many arguments for command");
        }
    }

    /**
     * Executes the command after validating that no additional arguments are provided.
     *
     * @param commandLine The parsed command line
     * @throws Exception If an error occurs during command execution
     */
    public void executeCommand(CommandLine commandLine) throws Exception {
        validateNoFiles(commandLine);
        executeCommand();
    }

    /**
     * Executes the command's specific functionality.
     * This method must be implemented by subclasses.
     *
     * @throws Exception If an error occurs during command execution
     */
    protected abstract void executeCommand() throws Exception;
}
