package io.github.decentralizedidentity.didwebvh.wizard;

/**
 * Terminal I/O abstraction for the wizard.  A {@link ConsoleWizardIo} backs the
 * interactive run; tests supply a scripted implementation.
 */
public interface WizardIo {

    /**
     * Prints {@code prompt} (no trailing newline added) and reads one line from the user.
     * Returns {@code null} if input is exhausted.
     */
    String readLine(String prompt);

    /** Prints a line of output. */
    void println(String message);

    /** Prints a line of error output. */
    void printError(String message);
}
