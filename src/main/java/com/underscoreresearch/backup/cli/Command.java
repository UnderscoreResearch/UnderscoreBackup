package com.underscoreresearch.backup.cli;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.cli.CommandLine;

import com.underscoreresearch.backup.configuration.InstanceFactory;

public abstract class Command {
    public abstract void executeCommand(CommandLine commandLine) throws Exception;

    public static String args(Class<Command> clz) {
        CommandPlugin plugin = (CommandPlugin) clz.getAnnotation(CommandPlugin.class);
        if (plugin != null)
            return plugin.args();
        return null;
    }

    public static String name(Class clz) {
        CommandPlugin plugin = (CommandPlugin) clz.getAnnotation(CommandPlugin.class);
        if (plugin != null)
            return plugin.value();
        return null;
    }

    public static String description(Class clz) {
        CommandPlugin plugin = (CommandPlugin) clz.getAnnotation(CommandPlugin.class);
        if (plugin != null)
            return plugin.description();
        return null;
    }

    public static boolean needPrivateKey(Class clz) {
        CommandPlugin plugin = (CommandPlugin) clz.getAnnotation(CommandPlugin.class);
        if (plugin != null)
            return plugin.needPrivateKey();
        return true;
    }

    public String name() {
        return name(this.getClass());
    }

    public String description() {
        return name(this.getClass());
    }

    public boolean needPrivateKey() {
        return needPrivateKey(this.getClass());
    }

    public static List<Class<Command>> allCommandClasses() {
        List<Class<Command>> commands = new ArrayList<>();
        InstanceFactory.getReflections().getTypesAnnotatedWith(CommandPlugin.class).stream()
                .map(t -> (Class<Command>) t).forEach(commands::add);
        commands.sort(Comparator.comparing(Command::name));
        return commands;
    }

    public static Class<Command> findCommandClass(String name) {
        for (Class<Command> command : Command.allCommandClasses()) {
            if (Command.name(command).equals(name)) {
                return command;
            }
        }
        return null;
    }
}
