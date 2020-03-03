package com.underscoreresearch.backup.cli;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.utils.InternalStateLogger;

@Slf4j
public final class Main {
    public static ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);

    public static void help() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("underscore-backup [OPTION]... [COMMAND]...\n ", InstanceFactory.getInstance(Options.class));
        System.out.println();
        System.out.println("Commands:");

        for (Class<Command> command : Command.allCommandClasses()) {
            System.out.println();
            System.out.println(Command.name(command) + " " + Command.args(command));
            System.out.println("\t" + Command.description(command));
        }

        System.out.println();
    }

    public static void main(String[] argv) {
        InstanceFactory.initialize(argv);
        try {
            CommandLine commandLine = InstanceFactory.getInstance(CommandLine.class);
            if (commandLine.getArgList().size() == 0) {
                help();
            } else {
                Class<Command> command = Command.findCommandClass(commandLine.getArgList().get(0));
                if (command != null) {
                    CommandPlugin commandDef = command.getAnnotation(CommandPlugin.class);

                    if (commandDef.needConfiguration()) {
                        try {
                            ConfigurationValidator.validateConfiguration(
                                    InstanceFactory.getInstance(BackupConfiguration.class),
                                    commandDef.readonlyRepository());

                            scheduledThreadPoolExecutor.scheduleAtFixedRate(new InternalStateLogger(), 1, 1,
                                    TimeUnit.MINUTES);
                        } catch (IllegalArgumentException exc) {
                            System.out.println(exc.getMessage());
                            System.exit(1);
                        }

                        InstanceFactory.getInstance(MetadataRepository.class).open(commandDef.readonlyRepository());
                    }

                    InstanceFactory.getInstance(command).executeCommand(commandLine);
                    System.exit(0);
                }

                System.out.println("Unknown command: " + commandLine.getArgList().get(0));
                System.out.println();
                help();
                System.exit(1);
            }
            System.exit(0);
        } catch (Exception exc) {
            Throwable parseException = exc;
            if (parseException.getCause() instanceof ParseException) {
                parseException = parseException.getCause();
            }
            if (parseException instanceof ParseException) {
                System.err.println(parseException.getMessage());
                System.err.println();
                help();
                System.exit(1);
            }
            log.error("Fatal exception", exc);
            System.exit(2);
        }
    }
}
