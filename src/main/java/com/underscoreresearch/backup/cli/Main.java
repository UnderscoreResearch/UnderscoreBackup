package com.underscoreresearch.backup.cli;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.SystemUtils;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.utils.StateLogger;

@Slf4j
public final class Main {
    public static ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder().setNameFormat("StateLogger-%d").build());

    public static void help() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("underscore-backup [OPTION]... [COMMAND]...\n ", InstanceFactory.getInstance(Options.class));
        System.out.println();
        System.out.println("Commands:");

        for (Class<? extends Command> command : Command.allCommandClasses()) {
            System.out.println();
            System.out.println(Command.name(command) + " " + Command.args(command));
            System.out.println("\t" + Command.description(command));
        }

        System.out.println();
    }

    public static void main(String[] argv) {
        if (!Strings.isNullOrEmpty(System.getProperty("startup.directory"))) {
            File chdir = new File(System.getProperty("startup.directory"));
            if (chdir.exists()) {
                try {
                    System.setProperty("user.dir", chdir.getCanonicalPath());
                } catch (IOException e) {
                    log.error("Invalid current working directory {}", System.getProperty("startup.directory"), e);
                }
            }
        }

        InstanceFactory.initialize(argv, null);

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                log.error("Uncaught exception from thread {} (Id {})", t.getName(), t.getId(), e);
            }
        });

        try {
            CommandLine commandLine = InstanceFactory.getInstance(CommandLine.class);

            if (commandLine.getArgList().size() == 0) {
                help();
                System.exit(0);
            }
            Class<? extends Command> command = Command.findCommandClass(commandLine.getArgList().get(0));
            if (command != null) {
                CommandPlugin commandDef = command.getAnnotation(CommandPlugin.class);

                if (commandDef.needConfiguration()) {
                    if (!InstanceFactory.hasConfiguration(commandDef.readonlyRepository())) {
                        System.exit(1);
                    }

                    InstanceFactory.getInstance(MetadataRepository.class).open(commandDef.readonlyRepository());
                }

                scheduledThreadPoolExecutor.scheduleAtFixedRate(() -> {
                            InstanceFactory.getInstance(StateLogger.class).logDebug();
                        }, 1, 1,
                        TimeUnit.MINUTES);

                Command commandInstance = InstanceFactory.getInstance(command);
                commandInstance.executeCommand(commandLine);
                System.exit(0);
            }

            System.out.println("Unknown command: " + commandLine.getArgList().get(0));
            System.out.println();
            help();
            System.exit(1);

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
