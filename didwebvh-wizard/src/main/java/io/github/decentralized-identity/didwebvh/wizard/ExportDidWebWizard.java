package io.github.decentralizedidentity.didwebvh.wizard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.decentralizedidentity.didwebvh.core.DidWebVhState;
import io.github.decentralizedidentity.didwebvh.core.didweb.DidWebPublisher;
import io.github.decentralizedidentity.didwebvh.core.model.DidDocument;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.validate.ValidationResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Interactive "export parallel did:web document" wizard.  Resolves the active DID
 * from the local {@code did.jsonl}, runs {@link DidWebPublisher#toDidWeb(DidDocument)},
 * and writes the resulting DID Document to {@code did.json} (spec section 3.7.10).
 */
public final class ExportDidWebWizard {

    static final String DEFAULT_OUTPUT = "did.json";

    private static final Gson PRETTY = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private final WizardIo io;
    private final WizardPrompts ask;

    public ExportDidWebWizard(WizardIo io) {
        this.io = io;
        this.ask = new WizardPrompts(io);
    }

    /** Run the export flow against files in {@code workDir}. */
    public void run(Path workDir) {
        io.println("=== Export parallel did:web document ===");

        Path logPath = workDir.resolve(WizardFiles.DID_LOG);
        if (!Files.exists(logPath)) {
            throw new WizardException("No " + WizardFiles.DID_LOG + " found in "
                    + workDir.toAbsolutePath());
        }

        String rawLog = WizardFiles.read(logPath);
        String did = firstEntryId(rawLog);

        // Validate the log chain so we don't export from a broken log, but don't go
        // through DidResolver.resolveFromLog – that enforces witness-proof presence even
        // when we already trust the local store, and did-witness.json is optional here.
        DidWebVhState state = DidWebVhState.fromDidLog(did, rawLog);
        ValidationResult validation = state.validate();
        if (!validation.isValid()) {
            throw new WizardException("Log failed validation: " + validation.getFailureReason());
        }
        DidDocument resolved = latestDocument(state.getLogEntries());

        DidDocument webDoc = DidWebPublisher.toDidWeb(resolved);
        String didWebUrl = DidWebPublisher.toDidWebUrl(did);

        String outName = ask.askOptional(
                "Output filename [" + DEFAULT_OUTPUT + "]: ", DEFAULT_OUTPUT);
        Path outPath = workDir.resolve(outName);
        WizardFiles.write(outPath, PRETTY.toJson(webDoc.asJsonObject()));

        io.println("");
        io.println("Parallel did:web DID: " + didWebUrl);
        io.println("Wrote " + outName + " to " + workDir.toAbsolutePath());
        io.println("Publish it alongside " + WizardFiles.DID_LOG
                + " so resolvers can fetch the did:web document.");
    }

    private DidDocument latestDocument(List<LogEntry> entries) {
        if (entries.isEmpty()) {
            throw new WizardException("DID log is empty");
        }
        LogEntry last = entries.get(entries.size() - 1);
        if (last.getState() == null) {
            throw new WizardException("Last log entry has no DID Document state");
        }
        return new DidDocument(last.getState());
    }

    private String firstEntryId(String rawLog) {
        for (String line : rawLog.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return LogEntry.fromJsonLine(trimmed).getState().get("id").getAsString();
            }
        }
        throw new WizardException("DID log is empty");
    }
}
