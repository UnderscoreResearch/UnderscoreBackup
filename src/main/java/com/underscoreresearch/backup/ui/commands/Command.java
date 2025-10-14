package com.underscoreresearch.backup.ui.commands;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import lombok.Getter;
import org.apache.commons.cli.CommandLine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Abstract base class for all CLI commands in the backup system.
 * Commands are discovered through the CommandPlugin annotation and provide
 * functionality for various operations like backup, restore, configuration, etc.
 */
@Getter
public abstract class Command {
    /**
     *  Password of backup if provided.
     */
    private String password;

    /**
     * Gets the command line arguments description for a command class.
     *
     * @param clz The command class
     * @return The arguments description string
     */
    public static String args(Class<? extends Command> clz) {
        CommandPlugin plugin = clz.getAnnotation(CommandPlugin.class);
        if (plugin != null)
            return plugin.args();
        return null;
    }

    /**
     * Gets the name of a command class.
     *
     * @param clz The command class
     * @return The command name
     */
    public static String name(Class<? extends Command> clz) {
        CommandPlugin plugin = clz.getAnnotation(CommandPlugin.class);
        if (plugin != null)
            return plugin.value();
        return null;
    }

    /**
     * Gets the description of a command class.
     *
     * @param clz The command class
     * @return The command description
     */
    public static String description(Class<? extends Command> clz) {
        CommandPlugin plugin = clz.getAnnotation(CommandPlugin.class);
        if (plugin != null)
            return plugin.description();
        return null;
    }

    /**
     * Checks if a command requires a private key.
     *
     * @param clz The command class
     * @return True if the command needs a private key, false otherwise
     */
    public static boolean needPrivateKey(Class<? extends Command> clz) {
        CommandPlugin plugin = clz.getAnnotation(CommandPlugin.class);
        if (plugin != null)
            return plugin.needPrivateKey();
        return true;
    }

    /**
     * Gets a list of all command classes available in the system.
     *
     * @return A sorted list of command classes
     */
    @SuppressWarnings("unchecked")
    public static List<Class<? extends Command>> allCommandClasses() {
        List<Class<? extends Command>> commands = new ArrayList<>();

        InstanceFactory.getReflections().getTypesAnnotatedWith(CommandPlugin.class).stream()
                .map(t -> (Class<? extends Command>) t).forEach(commands::add);
        commands.sort(Comparator.comparing(Command::name));
        return commands;
    }

    /**
     * Finds a command class by name.
     *
     * @param name The command name to find
     * @return The command class, or null if not found
     */
    public static Class<? extends Command> findCommandClass(String name) {
        for (Class<? extends Command> command : Command.allCommandClasses()) {
            if (Command.name(command).equals(name)) {
                return command;
            }
        }
        return null;
    }

    /**
     * Executes the command with the provided command line arguments.
     *
     * @param commandLine The parsed command line arguments
     * @throws Exception If an error occurs during command execution
     */
    public abstract void executeCommand(CommandLine commandLine) throws Exception;

    /**
     * Gets the name of this command.
     *
     * @return The command name
     */
    public String name() {
        return name(this.getClass());
    }

    /**
     * Gets the description of this command.
     *
     * @return The command description
     */
    public String description() {
        return name(this.getClass());
    }

    /**
     * Sets the password for this command.
     *
     * @param password The password to set
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Checks if this command requires a private key.
     *
     * @return True if the command needs a private key, false otherwise
     */
    public boolean needPrivateKey() {
        return needPrivateKey(this.getClass());
    }
}
