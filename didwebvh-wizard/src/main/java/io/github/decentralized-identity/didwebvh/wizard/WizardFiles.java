package io.github.decentralizedidentity.didwebvh.wizard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Standard filenames used by the wizard alongside helpers to read/write them. */
final class WizardFiles {

    static final String DID_LOG = "did.jsonl";
    static final String DID_SECRETS = "did-secrets.json";
    static final String DID_WITNESS = "did-witness.json";
    static final String NEXT_KEY_SECRETS = "did-next-key.json";
    static final String WITNESSES_DIR = "witnesses";

    private WizardFiles() {
    }

    static void write(Path path, String contents) {
        try {
            Files.write(path, contents.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new WizardException("Failed to write " + path + ": " + e.getMessage(), e);
        }
    }

    static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new WizardException("Failed to read " + path + ": " + e.getMessage(), e);
        }
    }

    static void appendLine(Path path, String line) {
        String existing = "";
        if (Files.exists(path)) {
            existing = read(path);
            if (!existing.isEmpty() && !existing.endsWith("\n")) {
                existing = existing + "\n";
            }
        }
        write(path, existing + line);
    }
}
