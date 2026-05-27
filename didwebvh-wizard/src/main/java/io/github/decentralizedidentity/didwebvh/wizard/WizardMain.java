package io.github.decentralizedidentity.didwebvh.wizard;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/** Command-line entry point for the did:webvh interactive wizard. */
@Command(
        name = "didwebvh-wizard",
        mixinStandardHelpOptions = true,
        version = "didwebvh-wizard 0.1.0-SNAPSHOT",
        description = "Interactive wizard for creating, updating, and resolving did:webvh DIDs."
)
public final class WizardMain implements Callable<Integer> {

    @Option(names = {"-d", "--dir"},
            description = "Working directory for DID files (default: current directory).")
    private Path workDir = Paths.get(".");

    @Option(names = {"-a", "--action"},
            description = "Skip the main menu and run a single action: "
                    + "create | update | resolve | export.")
    private String action;

    public static void main(String[] args) {
        int exit = new CommandLine(new WizardMain()).execute(args);
        System.exit(exit);
    }

    @Override
    public Integer call() {
        WizardIo io = new ConsoleWizardIo();
        return run(io);
    }

    /** Package-visible entry point used by tests. */
    int run(WizardIo io) {
        try {
            ensureWorkDir();
            if (action != null) {
                return runAction(io, action);
            }
            return runMenu(io);
        } catch (WizardException e) {
            io.printError("Error: " + e.getMessage());
            return 1;
        }
    }

    private int runMenu(WizardIo io) {
        WizardPrompts ask = new WizardPrompts(io);
        while (true) {
            io.println("");
            io.println("=== did:webvh Wizard ===");
            io.println("Working directory: " + workDir.toAbsolutePath());
            io.println("  1. Create a new DID");
            io.println("  2. Update an existing DID");
            io.println("  3. Resolve a DID");
            io.println("  4. Export parallel did:web document");
            io.println("  5. Exit");
            int choice = ask.askChoice("Choose 1-5: ", 5);
            if (choice == 5) {
                io.println("Goodbye.");
                return 0;
            }
            try {
                runAction(io, actionForChoice(choice));
            } catch (WizardException e) {
                io.printError("Error: " + e.getMessage());
            }
        }
    }

    private int runAction(WizardIo io, String name) {
        switch (name.toLowerCase()) {
            case "create":
                new CreateWizard(io).run(workDir);
                return 0;
            case "update":
                new UpdateWizard(io).run(workDir);
                return 0;
            case "resolve":
                new ResolveWizard(io).run(workDir);
                return 0;
            case "export":
                new ExportDidWebWizard(io).run(workDir);
                return 0;
            default:
                throw new WizardException("Unknown action: " + name
                        + " (expected create | update | resolve | export)");
        }
    }

    private static String actionForChoice(int choice) {
        switch (choice) {
            case 1: return "create";
            case 2: return "update";
            case 3: return "resolve";
            case 4: return "export";
            default: throw new WizardException("Unknown menu choice: " + choice);
        }
    }

    private void ensureWorkDir() {
        try {
            if (!Files.exists(workDir)) {
                Files.createDirectories(workDir);
            }
        } catch (IOException e) {
            throw new WizardException("Could not create working directory " + workDir
                    + ": " + e.getMessage(), e);
        }
    }

    void setWorkDir(Path workDir) {
        this.workDir = workDir;
    }

    void setAction(String action) {
        this.action = action;
    }
}
