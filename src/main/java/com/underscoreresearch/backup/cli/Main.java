package com.underscoreresearch.backup.cli;

import com.google.common.base.Strings;
import com.google.inject.ProvisionException;
import com.google.inject.spi.Message;
import com.underscoreresearch.backup.configuration.EncryptionModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.RepositoryOpenMode;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.utils.SingleTaskScheduler;
import com.underscoreresearch.backup.utils.StateLogger;
import com.underscoreresearch.backup.utils.state.MachineState;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.underscoreresearch.backup.configuration.CommandLineModule.SOURCE_CONFIG;

@Slf4j
public final class Main {
    public static SingleTaskScheduler taskScheduler = new SingleTaskScheduler("StateLogger");

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
                    log.error("Invalid current working directory \"{}\"", System.getProperty("startup.directory"), e);
                }
            }
        }

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                log.error("Uncaught exception from thread \"{}\" (Id \"{}\")", t.getName(), t.threadId(), e);
            }
        });

        try {
            InstanceFactory.initialize(argv, null, null);

            CommandLine commandLine = InstanceFactory.getInstance(CommandLine.class);

            if (commandLine.getArgList().isEmpty()) {
                help();
                System.exit(0);
            }
            Class<? extends Command> command = Command.findCommandClass(commandLine.getArgList().get(0));
            if (command != null) {
                CommandPlugin commandDef = command.getAnnotation(CommandPlugin.class);

                if (commandDef.needConfiguration()) {
                    if (!InstanceFactory.hasConfiguration(commandDef.readonlyRepository())) {
                        validateMainConfiguration(commandDef, () -> InstanceFactory.getInstance(BackupConfiguration.class), false);
                        if (InstanceFactory.getAdditionalSource() != null) {
                            validateMainConfiguration(commandDef,
                                    () -> InstanceFactory.getInstance(SOURCE_CONFIG, BackupConfiguration.class), true);
                        }
                    }

                    InstanceFactory.getInstance(MetadataRepository.class).open(
                            commandDef.readonlyRepository() ? RepositoryOpenMode.READ_ONLY : RepositoryOpenMode.READ_WRITE
                    );
                }

                if (commandDef.preferNice()) {
                    InstanceFactory.getInstance(MachineState.class).lowPriority();
                }

                if (InstanceFactory.getAdditionalSource() != null && !commandDef.supportSource()) {
                    log.error("The specified command can not operate on a secondary source");
                    help();
                    System.exit(1);
                }

                Command commandInstance = InstanceFactory.getInstance(command);

                if (commandDef.needPrivateKey()) {
                    EncryptionIdentity key = InstanceFactory.getInstance(EncryptionIdentity.class);
                    String password = EncryptionModule.getPassword();
                    key.getPrivateIdentity(password);
                    commandInstance.setPassword(password);
                }

                taskScheduler.scheduleAtFixedRate(() -> {
                            Thread thread = new Thread(StateLogger::logDebug, "StateLogger");
                            thread.setDaemon(true);
                            thread.start();
                        }, 1, 1,
                        TimeUnit.MINUTES);

                commandInstance.executeCommand(commandLine);
                System.exit(0);
            }

            System.out.println("Unknown command: " + commandLine.getArgList().get(0));
            System.out.println();
            help();
            System.exit(1);

            System.exit(0);
        } catch (Throwable exc) {
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

    private static void validateMainConfiguration(CommandPlugin commandDef,
                                                  Supplier<BackupConfiguration> configFetcher,
                                                  boolean source) {
        try {
            ConfigurationValidator.validateConfiguration(configFetcher.get(),
                    commandDef.readonlyRepository(), source);
        } catch (ProvisionException exc) {
            for (Message message : exc.getErrorMessages()) {
                log.error(message.getMessage());
            }
            System.exit(1);
        } catch (IllegalArgumentException exc) {
            log.error(exc.getMessage());
            System.exit(1);
        }
    }
}
