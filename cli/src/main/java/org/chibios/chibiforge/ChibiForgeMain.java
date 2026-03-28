package org.chibios.chibiforge;

import org.chibios.chibiforge.cli.GenerateCommand;
import picocli.CommandLine;

@CommandLine.Command(
        name = "chibiforge",
        description = "ChibiForge - Component-based configuration and code generation for embedded projects",
        mixinStandardHelpOptions = true,
        version = "ChibiForge 1.0.0-SNAPSHOT",
        subcommands = {GenerateCommand.class}
)
public class ChibiForgeMain implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ChibiForgeMain()).execute(args);
        System.exit(exitCode);
    }
}
