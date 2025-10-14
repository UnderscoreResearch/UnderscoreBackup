package com.underscoreresearch.backup.ui.commands;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark classes as command plugins for the CLI system.
 * This annotation provides metadata about commands such as name, description,
 * and various requirements for execution.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CommandPlugin {
    /**
     * The name of the command as it appears on the command line.
     *
     * @return The command name
     */
    String value();

    /**
     * Description of the command line arguments for this command.
     *
     * @return The arguments description
     */
    String args() default "";

    /**
     * A description of what the command does.
     *
     * @return The command description
     */
    String description();

    /**
     * Whether the command requires a private key to execute.
     *
     * @return True if a private key is required, false otherwise
     */
    boolean needPrivateKey() default true;

    /**
     * Whether the command requires a configuration file to execute.
     *
     * @return True if configuration is required, false otherwise
     */
    boolean needConfiguration() default true;

    /**
     * Whether the command only reads from the repository without modifying it.
     *
     * @return True if the command is read-only, false otherwise
     */
    boolean readonlyRepository() default true;

    /**
     * Whether the command supports specifying a source.
     *
     * @return True if the command supports source specification, false otherwise
     */
    boolean supportSource() default false;

    /**
     * Whether the command should preferably run at a lower priority.
     *
     * @return True if the command should run at a lower priority, false otherwise
     */
    boolean preferNice() default false;
}
