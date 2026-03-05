package io.themis.cli;

import io.themis.cli.command.FuzzValidateCommand;
import io.themis.cli.command.GenerateTestsCommand;
import io.themis.cli.command.PipelineCommand;
import io.themis.cli.command.StaticDetectCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "themis",
    mixinStandardHelpOptions = true,
    version = "0.1.0",
    subcommands = {
        StaticDetectCommand.class,
        GenerateTestsCommand.class,
        FuzzValidateCommand.class,
        PipelineCommand.class
    }
)
public class ThemisCliMain implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        int code = new CommandLine(new ThemisCliMain()).execute(args);
        System.exit(code);
    }
}
