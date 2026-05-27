package io.github.decentralizedidentity.didwebvh.wizard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.decentralizedidentity.didwebvh.core.DidWebVhException;
import io.github.decentralizedidentity.didwebvh.core.model.ResolveResult;
import io.github.decentralizedidentity.didwebvh.core.resolve.DidResolver;
import io.github.decentralizedidentity.didwebvh.core.resolve.ResolveOptions;

import java.nio.file.Files;
import java.nio.file.Path;

/** Interactive "resolve a DID" wizard. Supports remote HTTPS and local file resolution. */
public final class ResolveWizard {

    private final WizardIo io;
    private final WizardPrompts ask;
    private final DidResolver resolver;
    private static final Gson PRETTY = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    public ResolveWizard(WizardIo io) {
        this(io, new DidResolver());
    }

    ResolveWizard(WizardIo io, DidResolver resolver) {
        this.io = io;
        this.ask = new WizardPrompts(io);
        this.resolver = resolver;
    }

    /** Run the resolve flow.  {@code workDir} is used as the default for local-file lookups. */
    public void run(Path workDir) {
        io.println("=== Resolve a did:webvh DID ===");
        io.println("  1) Resolve over HTTPS");
        io.println("  2) Resolve from a local did.jsonl file");
        int choice = ask.askChoice("Choose 1 or 2: ", 2);

        ResolveOptions options = buildOptions();

        ResolveResult result;
        try {
            if (choice == 1) {
                String did = ask.askRequired("DID: ");
                result = resolver.resolve(did, options);
            } else {
                String pathStr = ask.askOptional(
                        "did.jsonl path [" + workDir.resolve(WizardFiles.DID_LOG) + "]: ",
                        workDir.resolve(WizardFiles.DID_LOG).toString());
                Path path = Path.of(pathStr);
                if (!Files.exists(path)) {
                    throw new WizardException("File not found: " + path);
                }
                String did = ask.askOptional(
                        "Expected DID (blank to skip validation of id): ", null);
                result = resolver.resolveFromLog(WizardFiles.read(path), did, options);
            }
        } catch (DidWebVhException e) {
            io.printError("Resolution failed: " + e.getMessage());
            return;
        }

        io.println("");
        if (result.getError() != null) {
            io.println("Error: " + result.getError());
            if (result.getProblemDetails() != null) {
                io.println("Details:");
                io.println(PRETTY.toJson(result.getProblemDetails()));
            }
            return;
        }
        io.println("DID Document:");
        if (result.getDidDocument() != null) {
            io.println(PRETTY.toJson(result.getDidDocument().asJsonObject()));
        } else {
            io.println("(none)");
        }
        io.println("");
        io.println("Resolution metadata:");
        io.println(PRETTY.toJson(result.getMetadata()));
    }

    private ResolveOptions buildOptions() {
        if (!ask.askYesNo("Filter by a specific version?", false)) {
            return ResolveOptions.defaults();
        }
        io.println("  1) versionId  2) versionTime  3) versionNumber");
        int sub = ask.askChoice("Choose 1-3: ", 3);
        ResolveOptions.Builder builder = ResolveOptions.builder();
        switch (sub) {
            case 1:
                builder.versionId(ask.askRequired("versionId: "));
                break;
            case 2:
                builder.versionTime(ask.askRequired("versionTime (ISO-8601): "));
                break;
            case 3:
                builder.versionNumber(ask.askInt("versionNumber: ", null));
                break;
            default:
                break;
        }
        return builder.build();
    }
}
