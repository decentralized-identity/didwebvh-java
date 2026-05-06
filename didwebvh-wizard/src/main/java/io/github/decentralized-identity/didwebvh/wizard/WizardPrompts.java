package io.github.decentralizedidentity.didwebvh.wizard;

/** Prompt helpers shared by all wizards. */
final class WizardPrompts {

    private final WizardIo io;

    WizardPrompts(WizardIo io) {
        this.io = io;
    }

    /** Read a required non-empty string; keeps asking until one is provided. */
    String askRequired(String prompt) {
        while (true) {
            String line = io.readLine(prompt);
            if (line == null) {
                throw new WizardException("Input ended while expecting a value");
            }
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
            io.printError("A value is required.");
        }
    }

    /** Read an optional string (empty input returns {@code defaultValue}, which may be null). */
    String askOptional(String prompt, String defaultValue) {
        String line = io.readLine(prompt);
        if (line == null) {
            return defaultValue;
        }
        String trimmed = line.trim();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }

    /** Ask a yes/no question.  Defaults to {@code defaultValue} on empty input. */
    boolean askYesNo(String prompt, boolean defaultValue) {
        String suffix = defaultValue ? " [Y/n]: " : " [y/N]: ";
        while (true) {
            String line = io.readLine(prompt + suffix);
            if (line == null) {
                return defaultValue;
            }
            String trimmed = line.trim().toLowerCase();
            if (trimmed.isEmpty()) {
                return defaultValue;
            }
            if (trimmed.equals("y") || trimmed.equals("yes")) {
                return true;
            }
            if (trimmed.equals("n") || trimmed.equals("no")) {
                return false;
            }
            io.printError("Please answer 'y' or 'n'.");
        }
    }

    /** Ask for an integer; empty returns {@code defaultValue} if non-null, otherwise re-asks. */
    int askInt(String prompt, Integer defaultValue) {
        while (true) {
            String line = io.readLine(prompt);
            if (line == null) {
                if (defaultValue != null) {
                    return defaultValue;
                }
                throw new WizardException("Input ended while expecting an integer");
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty() && defaultValue != null) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(trimmed);
            } catch (NumberFormatException e) {
                io.printError("Not a valid integer: '" + trimmed + "'");
            }
        }
    }

    /** Ask for a menu choice between 1 and {@code max}; re-prompts on invalid input. */
    int askChoice(String prompt, int max) {
        while (true) {
            int value = askInt(prompt, null);
            if (value >= 1 && value <= max) {
                return value;
            }
            io.printError("Choice must be between 1 and " + max + ".");
        }
    }
}
