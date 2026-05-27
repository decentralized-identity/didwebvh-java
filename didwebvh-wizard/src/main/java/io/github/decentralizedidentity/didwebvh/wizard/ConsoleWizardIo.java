package io.github.decentralizedidentity.didwebvh.wizard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Default {@link WizardIo} backed by {@link System#in} / {@link System#out}. */
public final class ConsoleWizardIo implements WizardIo {

    private final BufferedReader reader;
    private final PrintStream out;
    private final PrintStream err;

    public ConsoleWizardIo() {
        this(System.in, System.out, System.err);
    }

    public ConsoleWizardIo(InputStream in, PrintStream out, PrintStream err) {
        this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.out = out;
        this.err = err;
    }

    @Override
    public String readLine(String prompt) {
        if (prompt != null) {
            out.print(prompt);
            out.flush();
        }
        try {
            return reader.readLine();
        } catch (IOException e) {
            throw new WizardException("Failed to read input: " + e.getMessage(), e);
        }
    }

    @Override
    public void println(String message) {
        out.println(message);
    }

    @Override
    public void printError(String message) {
        err.println(message);
    }
}
