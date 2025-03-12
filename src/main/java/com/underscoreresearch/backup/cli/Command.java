package com.underscoreresearch.backup.cli;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import org.apache.commons.cli.CommandLine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class Command {
    private String password;

    public static String args(Class<? extends Command> clz) {
        CommandPlugin plugin = clz.getAnnotation(CommandPlugin.class);
        if (plugin != null)
            return plugin.args();
        return null;
    }

    public static String name(Class<? extends Command> clz) {
        CommandPlugin plugin = clz.getAnnotation(CommandPlugin.class);
        if (plugin != null)
            return plugin.value();
        return null;
    }

    public static String description(Class<? extends Command> clz) {
        CommandPlugin plugin = clz.getAnnotation(CommandPlugin.class);
        if (plugin != null)
            return plugin.description();
        return null;
    }

    public static boolean needPrivateKey(Class<? extends Command> clz) {
        CommandPlugin plugin = clz.getAnnotation(CommandPlugin.class);
        if (plugin != null)
            return plugin.needPrivateKey();
        return true;
    }

    @SuppressWarnings("unchecked")
    public static List<Class<? extends Command>> allCommandClasses() {
        List<Class<? extends Command>> commands = new ArrayList<>();

        InstanceFactory.getReflections().getTypesAnnotatedWith(CommandPlugin.class).stream()
                .map(t -> (Class<? extends Command>) t).forEach(commands::add);
        commands.sort(Comparator.comparing(Command::name));
        return commands;
    }

    public static Class<? extends Command> findCommandClass(String name) {
        for (Class<? extends Command> command : Command.allCommandClasses()) {
            if (Command.name(command).equals(name)) {
                return command;
            }
        }
        return null;
    }

    public abstract void executeCommand(CommandLine commandLine) throws Exception;

    public String name() {
        return name(this.getClass());
    }

    public String description() {
        return name(this.getClass());
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean needPrivateKey() {
        return needPrivateKey(this.getClass());
    }
}
