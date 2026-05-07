package io.github.decentralizedidentity.didwebvh.wizard;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.github.decentralizedidentity.didwebvh.core.DidWebVh;
import io.github.decentralizedidentity.didwebvh.core.DidWebVhState;
import io.github.decentralizedidentity.didwebvh.core.model.DidDocument;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.update.UpdateDidResult;
import io.github.decentralizedidentity.didwebvh.core.url.DidWebVhUrl;
import io.github.decentralizedidentity.didwebvh.core.validate.ValidationResult;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig;
import io.github.decentralizedidentity.didwebvh.signing.local.LocalKeySigner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Interactive "update / migrate / deactivate" wizard. */
public final class UpdateWizard {

    private final WizardIo io;
    private final WizardPrompts ask;
    private final WizardWitnessProofs witnessProofs;

    public UpdateWizard(WizardIo io) {
        this.io = io;
        this.ask = new WizardPrompts(io);
        this.witnessProofs = new WizardWitnessProofs(io);
    }

    /** Run the update flow against files in {@code workDir}. */
    public void run(Path workDir) {
        io.println("=== Update an existing did:webvh DID ===");

        Path logPath = workDir.resolve(WizardFiles.DID_LOG);
        Path secretsPath = workDir.resolve(WizardFiles.DID_SECRETS);
        if (!Files.exists(logPath)) {
            throw new WizardException("No " + WizardFiles.DID_LOG + " found in "
                    + workDir.toAbsolutePath());
        }
        if (!Files.exists(secretsPath)) {
            throw new WizardException("No " + WizardFiles.DID_SECRETS + " found in "
                    + workDir.toAbsolutePath());
        }

        LocalKeySigner signer = LocalKeySigner.fromJson(WizardFiles.read(secretsPath));
        String rawLog = WizardFiles.read(logPath);
        List<LogEntry> entries = parseLog(rawLog);
        if (entries.isEmpty()) {
            throw new WizardException("DID log is empty");
        }
        String did = entries.get(0).getState().get("id").getAsString();
        DidWebVhState state = DidWebVhState.fromDidLog(did, rawLog);
        ValidationResult existing = state.validate();
        if (!existing.isValid()) {
            throw new WizardException("Existing log failed validation: "
                    + existing.getFailureReason());
        }
        if (state.isDeactivated()) {
            throw new WizardException("DID is already deactivated; no further updates allowed.");
        }

        io.println("Loaded " + entries.size() + " existing log entries for " + did);
        io.println("");
        io.println("Operation:");
        io.println("  1) Modify the DID document or parameters");
        io.println("  2) Migrate to a new domain");
        io.println("  3) Deactivate the DID");
        int choice = ask.askChoice("Choose 1-3: ", 3);

        Parameters priorParams = state.accumulateParameters();
        UpdateDidResult result;
        switch (choice) {
            case 1:
                result = runModify(state, signer, did, workDir);
                break;
            case 2:
                result = runMigrate(state, signer);
                break;
            case 3:
                result = runDeactivate(state, signer, workDir);
                break;
            default:
                throw new WizardException("Unexpected choice: " + choice);
        }

        // Spec 3.7.8: did-witness.json MUST be published BEFORE did.jsonl.
        witnessProofs.collectForNewEntries(priorParams, result.getNewEntries(), workDir);

        for (LogEntry entry : result.getNewEntries()) {
            WizardFiles.appendLine(logPath, entry.toJsonLine());
        }
        io.println("");
        io.println("Wrote " + result.getNewEntries().size() + " new entr"
                + (result.getNewEntries().size() == 1 ? "y" : "ies")
                + " to " + WizardFiles.DID_LOG);
        io.println("New DID state id: " + result.getLogEntry().getState().get("id").getAsString());
    }

    private UpdateDidResult runModify(DidWebVhState state, LocalKeySigner signer,
                                      String did, Path workDir) {
        JsonObject newDocument = null;
        if (ask.askYesNo("Replace the DID Document?", false)) {
            String json = ask.askRequired("New DID Document JSON (single line): ");
            newDocument = parseAndValidateDocument(json, did,
                    state.accumulateParameters());
        }

        Parameters changed = null;
        if (ask.askYesNo("Change any parameters?", false)) {
            changed = promptParameterChanges(state, workDir);
        }

        return DidWebVh.update(state, signer)
                .newDocument(newDocument)
                .changedParameters(changed)
                .execute();
    }

    private JsonObject parseAndValidateDocument(String json, String expectedDid,
                                                Parameters activeParams) {
        JsonObject obj;
        try {
            obj = JsonParser.parseString(json).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new WizardException("Invalid DID Document JSON: " + e.getMessage(), e);
        }
        DidDocument doc = new DidDocument(obj);
        String id = doc.getId();
        if (id == null || id.isEmpty()) {
            throw new WizardException("DID Document is missing required 'id' field");
        }
        if (!id.equals(expectedDid)) {
            classifyIdChange(id, expectedDid, activeParams);
        }
        return obj;
    }

    /**
     * Reject mismatched document {@code id}s with a message tailored to the root cause.
     * A SCID or method change is always forbidden; a domain/path change is handled by
     * the dedicated Migrate operation (and requires {@code portable=true}).
     */
    private void classifyIdChange(String newId, String currentId, Parameters activeParams) {
        DidWebVhUrl currentUrl;
        DidWebVhUrl newUrl;
        try {
            currentUrl = DidWebVhUrl.parse(currentId);
        } catch (RuntimeException e) {
            throw new WizardException("Current DID is not a valid did:webvh URL: "
                    + currentId, e);
        }
        try {
            newUrl = DidWebVhUrl.parse(newId);
        } catch (RuntimeException e) {
            throw new WizardException("New DID Document 'id' is not a valid did:webvh URL: "
                    + newId + " – method/type change is not allowed", e);
        }
        if (!currentUrl.getScid().equals(newUrl.getScid())) {
            throw new WizardException("DID Document 'id' changes the SCID ("
                    + currentUrl.getScid() + " → " + newUrl.getScid()
                    + "). SCID is immutable.");
        }
        // Same SCID and method → only the domain or path segments differ.
        String portableHint = Boolean.TRUE.equals(activeParams.getPortable())
                ? "use the Migrate operation instead of Modify"
                : "this DID is not portable (portable=false), so migration is not allowed";
        throw new WizardException("DID Document 'id' changes the domain/path ("
                + currentId + " → " + newId + "); " + portableHint + ".");
    }

    /**
     * Prompt for every mutable parameter in {@link Parameters}.  Returns an empty
     * {@code Parameters()} (meaning an empty {@code {}} delta) if the user declines
     * every field — the caller still gets a no-op parameter change marker.
     */
    private Parameters promptParameterChanges(DidWebVhState state, Path workDir) {
        Parameters current = state.accumulateParameters();
        Parameters changed = new Parameters();

        if (ask.askYesNo("Change updateKeys (key rotation)?", false)) {
            String line = ask.askRequired(
                    "New updateKeys (comma-separated multikeys): ");
            changed.setUpdateKeys(splitList(line));
        }

        if (ask.askYesNo("Change nextKeyHashes (pre-rotation)?", false)) {
            io.println("  1) Clear (disable pre-rotation)");
            io.println("  2) Enter hash(es) manually");
            int mode = ask.askChoice("Choose 1-2: ", 2);
            if (mode == 1) {
                changed.setNextKeyHashes(Collections.emptyList());
            } else {
                String line = ask.askRequired(
                        "New nextKeyHashes (comma-separated): ");
                changed.setNextKeyHashes(splitList(line));
            }
            maybeWarnUnreachableHash(changed.getNextKeyHashes());
        }

        if (ask.askYesNo("Change witness configuration?", false)) {
            changed.setWitness(readWitnessConfig(workDir, current.getWitness()));
        }

        if (ask.askYesNo("Change watchers?", false)) {
            changed.setWatchers(readWatchers(current.getWatchers()));
        }

        if (Boolean.TRUE.equals(current.getPortable())
                && ask.askYesNo("Disable portability (portable=false, PERMANENT)?", false)) {
            changed.setPortable(Boolean.FALSE);
        }

        if (ask.askYesNo("Change TTL?", false)) {
            changed.setTtl(ask.askInt("New TTL (seconds, 0 = no cache): ", null));
        }

        return changed;
    }

    /** Witness turned off produces an empty WitnessConfig; otherwise prompt for full config. */
    private WitnessConfig readWitnessConfig(Path workDir, WitnessConfig current) {
        if (ask.askYesNo("Clear witnesses (set to {})?", false)) {
            return WitnessConfig.empty();
        }
        return new WizardWitnessKeys(io).configure(workDir, current);
    }

    /**
     * Read watcher-list changes relative to {@code current}.  Blank keeps the list, a
     * comma-separated list of URLs appends them to what is already there, and
     * {@code "clear"} removes all watchers.  This matches the user's "add a watcher"
     * mental model while still allowing replacement by clearing first.
     */
    private List<String> readWatchers(List<String> current) {
        List<String> effective = current == null ? new ArrayList<>() : new ArrayList<>(current);
        if (effective.isEmpty()) {
            io.println("Current watchers: (none)");
        } else {
            io.println("Current watchers:");
            for (String w : effective) {
                io.println("  - " + w);
            }
        }
        String line = ask.askOptional(
                "Watchers to add (comma-separated, 'clear' to remove all, blank to keep): ",
                null);
        if (line == null) {
            return effective;
        }
        if ("clear".equalsIgnoreCase(line.trim())) {
            return Collections.emptyList();
        }
        for (String part : splitList(line)) {
            if (!effective.contains(part)) {
                effective.add(part);
            }
        }
        return effective;
    }

    private void maybeWarnUnreachableHash(List<String> hashes) {
        if (hashes == null || hashes.isEmpty()) {
            return;
        }
        io.println("NOTE: the corresponding next-key secret must be available at the "
                + "next update, or the DID becomes unrecoverable.");
    }

    private List<String> splitList(String line) {
        List<String> out = new ArrayList<>();
        for (String part : line.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private UpdateDidResult runMigrate(DidWebVhState state, LocalKeySigner signer) {
        if (!Boolean.TRUE.equals(state.accumulateParameters().getPortable())) {
            throw new WizardException(
                    "DID is not portable; cannot migrate. Migration requires portable=true.");
        }
        String newDomain = ask.askRequired("New domain: ");
        String newPath = ask.askOptional("New path (blank for none): ", null);
        return DidWebVh.migrate(state, signer, newDomain)
                .newPath(newPath)
                .execute();
    }

    private UpdateDidResult runDeactivate(DidWebVhState state, LocalKeySigner signer,
                                          Path workDir) {
        io.println("");
        io.println("WARNING: Deactivation is PERMANENT.  The DID cannot be re-activated.");
        String confirm = ask.askRequired("Type 'DEACTIVATE' to confirm: ");
        if (!"DEACTIVATE".equals(confirm)) {
            throw new WizardException("Deactivation not confirmed; aborting.");
        }
        LocalKeySigner nextSigner = null;
        List<String> nextHashes = state.accumulateParameters().getNextKeyHashes();
        if (nextHashes != null && !nextHashes.isEmpty()) {
            Path nextKeyPath = workDir.resolve(WizardFiles.NEXT_KEY_SECRETS);
            if (!Files.exists(nextKeyPath)) {
                throw new WizardException("Pre-rotation is active but "
                        + WizardFiles.NEXT_KEY_SECRETS + " was not found in "
                        + workDir.toAbsolutePath());
            }
            nextSigner = LocalKeySigner.fromJson(WizardFiles.read(nextKeyPath));
        }
        return DidWebVh.deactivate(state, signer)
                .nextRotationSigner(nextSigner)
                .execute();
    }

    private List<LogEntry> parseLog(String rawLog) {
        List<LogEntry> list = new ArrayList<>();
        for (String line : rawLog.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                list.add(LogEntry.fromJsonLine(trimmed));
            }
        }
        return list;
    }
}
