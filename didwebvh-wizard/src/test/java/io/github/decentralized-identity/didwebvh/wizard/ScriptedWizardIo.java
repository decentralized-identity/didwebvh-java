package io.github.decentralizedidentity.didwebvh.wizard;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Test {@link WizardIo} that replays pre-programmed answers and records output. */
final class ScriptedWizardIo implements WizardIo {

    private final Deque<String> inputs = new ArrayDeque<>();
    private final List<String> outputs = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    ScriptedWizardIo(String... answers) {
        for (String a : answers) {
            inputs.add(a);
        }
    }

    void enqueue(String... answers) {
        for (String a : answers) {
            inputs.add(a);
        }
    }

    @Override
    public String readLine(String prompt) {
        outputs.add("> " + (prompt == null ? "" : prompt));
        if (inputs.isEmpty()) {
            return null;
        }
        String next = inputs.removeFirst();
        outputs.add("< " + next);
        return next;
    }

    @Override
    public void println(String message) {
        outputs.add(message);
    }

    @Override
    public void printError(String message) {
        errors.add(message);
    }

    String allOutput() {
        return String.join("\n", outputs);
    }

    List<String> errors() {
        return errors;
    }

    int remainingInputs() {
        return inputs.size();
    }
}
