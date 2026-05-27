package io.github.decentralizedidentity.didwebvh.wizard;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.github.decentralizedidentity.didwebvh.core.DidWebVh;
import io.github.decentralizedidentity.didwebvh.core.create.CreateDidConfig;
import io.github.decentralizedidentity.didwebvh.core.create.CreateDidResult;
import io.github.decentralizedidentity.didwebvh.core.crypto.PreRotationHashGenerator;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig;
import io.github.decentralizedidentity.didwebvh.signing.local.LocalKeySigner;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Interactive "create a new DID" wizard. */
public final class CreateWizard {

    private final WizardIo io;
    private final WizardPrompts ask;

    public CreateWizard(WizardIo io) {
        this.io = io;
        this.ask = new WizardPrompts(io);
    }

    /**
     * Run the create flow, writing outputs to {@code workDir}.  Returns the created DID string.
     */
    public String run(Path workDir) {
        io.println("=== Create a new did:webvh DID ===");

        String domain = ask.askRequired("Web domain (e.g. example.com): ");
        String path = ask.askOptional("Path (optional, 'dids:issuer' style, blank to skip): ", null);

        LocalKeySigner signer = loadOrGenerateSigner(workDir);
        io.println("Authorization key multikey: " + signer.getPublicKeyMultikey());

        CreateDidConfig config = DidWebVh.create(domain, signer);
        if (path != null) {
            config.path(path);
        }

        List<String> alsoKnownAs = readList(
                "alsoKnownAs (comma-separated, blank for none): ");
        if (!alsoKnownAs.isEmpty()) {
            config.alsoKnownAs(alsoKnownAs);
        }

        List<String> controllers = readControllers();
        if (controllers != null) {
            config.controllers(controllers);
        }

        JsonObject additional = buildAdditionalDocumentContent();
        if (additional.size() > 0) {
            config.additionalDocumentContent(additional);
        }

        if (ask.askYesNo("Make this DID portable?", false)) {
            config.portable(true);
        }

        LocalKeySigner nextKeySigner = null;
        if (ask.askYesNo("Enable pre-rotation (generate a next key now)?", false)) {
            nextKeySigner = LocalKeySigner.generate();
            WizardFiles.write(workDir.resolve(WizardFiles.NEXT_KEY_SECRETS),
                    nextKeySigner.toJson());
            io.println("Next-rotation key saved to "
                    + WizardFiles.NEXT_KEY_SECRETS
                    + " (multikey: " + nextKeySigner.getPublicKeyMultikey() + ")");
            String hash = PreRotationHashGenerator.generateHash(
                    nextKeySigner.getPublicKeyMultikey());
            config.nextKeyHashes(Collections.singletonList(hash));
        }

        WitnessConfig witness = readWitnessConfig(workDir);
        if (witness != null) {
            config.witness(witness);
        }

        List<String> watchers = readList("Watchers (comma-separated URLs, blank for none): ");
        if (!watchers.isEmpty()) {
            config.watchers(watchers);
        }

        int ttl = ask.askInt("TTL seconds [default 3600, 0 = no cache]: ", 3600);
        if (ttl != 3600) {
            config.ttl(ttl);
        }

        CreateDidResult result = config.execute();

        // Spec 3.7.8: did-witness.json MUST be published BEFORE did.jsonl. If the
        // first entry already activates a witness set, resolvers expect proofs for
        // that entry too, so collect them before writing the log.
        if (witness != null && witness.isActive()) {
            new WizardWitnessProofs(io).collectForNewEntries(
                    Parameters.defaults(),
                    Collections.singletonList(result.getLogEntry()),
                    workDir);
        }

        WizardFiles.write(workDir.resolve(WizardFiles.DID_LOG), result.getLogLine());

        io.println("");
        io.println("DID created: " + result.getDid());
        io.println("Files written to " + workDir.toAbsolutePath() + ":");
        io.println("  - " + WizardFiles.DID_LOG);
        io.println("  - " + WizardFiles.DID_SECRETS + "   (KEEP SECURE)");
        if (nextKeySigner != null) {
            io.println("  - " + WizardFiles.NEXT_KEY_SECRETS + "  (KEEP SECURE)");
        }
        return result.getDid();
    }

    private LocalKeySigner loadOrGenerateSigner(Path workDir) {
        io.println("");
        io.println("Authorization key:");
        io.println("  1) Generate a new Ed25519 key");
        io.println("  2) Import from an existing " + WizardFiles.DID_SECRETS + " JSON file");
        int choice = ask.askChoice("Choose 1 or 2: ", 2);
        LocalKeySigner signer;
        if (choice == 1) {
            signer = LocalKeySigner.generate();
        } else {
            String pathStr = ask.askRequired("Path to existing key JSON: ");
            signer = LocalKeySigner.fromJson(WizardFiles.read(Path.of(pathStr)));
        }
        WizardFiles.write(workDir.resolve(WizardFiles.DID_SECRETS), signer.toJson());
        return signer;
    }

    private List<String> readList(String prompt) {
        String line = ask.askOptional(prompt, null);
        if (line == null) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String part : line.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    /**
     * Ask for the DID Document {@code controller} property.  Returns {@code null} to
     * keep the library default (single controller = the DID itself); an empty list
     * to emit no {@code controller} property; a non-empty list otherwise.
     */
    private List<String> readControllers() {
        io.println("");
        io.println("Controller (DID Document 'controller' property):");
        io.println("  blank   keep default (controller = the DID itself)");
        io.println("  '-'     omit the controller property entirely");
        io.println("  list    comma-separated DIDs to use as controller(s)");
        String line = ask.askOptional("Controllers: ", null);
        if (line == null) {
            return null;
        }
        if ("-".equals(line.trim())) {
            return Collections.emptyList();
        }
        List<String> controllers = new ArrayList<>();
        for (String part : line.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                controllers.add(trimmed);
            }
        }
        return controllers.isEmpty() ? null : controllers;
    }

    private JsonObject buildAdditionalDocumentContent() {
        JsonObject extra = new JsonObject();

        String services = ask.askOptional(
                "Services JSON array (paste one line, blank to skip): ", null);
        if (services != null) {
            try {
                JsonElement parsed = JsonParser.parseString(services);
                if (!parsed.isJsonArray()) {
                    throw new WizardException("Services must be a JSON array");
                }
                extra.add("service", parsed.getAsJsonArray());
            } catch (JsonSyntaxException e) {
                throw new WizardException("Invalid services JSON: " + e.getMessage(), e);
            }
        }
        return extra;
    }

    private WitnessConfig readWitnessConfig(Path workDir) {
        if (!ask.askYesNo("Configure witnesses?", false)) {
            return null;
        }
        return new WizardWitnessKeys(io).configure(workDir);
    }
}
