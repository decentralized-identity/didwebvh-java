package io.github.decentralizedidentity.didwebvh.core.create;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.ValidationException;
import io.github.decentralizedidentity.didwebvh.core.crypto.EntryHashGenerator;
import io.github.decentralizedidentity.didwebvh.core.crypto.ScidGenerator;
import io.github.decentralizedidentity.didwebvh.core.model.DataIntegrityProof;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.signing.ProofGenerator;
import io.github.decentralizedidentity.didwebvh.core.signing.Signer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Implements the DID creation flow from spec section 3.6.1.
 */
final class CreateDidOperation {

    private static final String METHOD_VERSION = "did:webvh:1.0";
    private static final String SCID_PLACEHOLDER = ScidGenerator.placeholder();

    private CreateDidOperation() {
    }

    static CreateDidResult execute(CreateDidConfig config) {
        validate(config);

        String signerMultikey = extractMultikey(config.getSigner());

        // Step 1: Build the DID string with {SCID} placeholder
        String didTemplate = buildDidTemplate(config.getDomain(), config.getPath());

        // Step 2: Build initial DID Document with {SCID} placeholders
        JsonObject docJson = buildDidDocument(didTemplate, signerMultikey, config);

        // Step 3: Build initial Parameters
        Parameters params = buildParameters(signerMultikey, config);

        // Step 4: Build preliminary log entry (no proof)
        String versionTime = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        LogEntry preliminary = new LogEntry()
                .setVersionId(SCID_PLACEHOLDER)
                .setVersionTime(versionTime)
                .setParameters(params)
                .setState(docJson);

        // Step 5: Generate SCID
        String scid = ScidGenerator.generate(preliminary);

        // Step 6: Replace all {SCID} placeholders with actual SCID
        String json = preliminary.toJsonLine();
        String resolved = json.replace(SCID_PLACEHOLDER, scid);
        LogEntry entry = LogEntry.fromJsonLine(resolved);

        // Step 7: Generate entry hash with SCID as predecessor
        String entryJson = entry.toJsonLine();
        String entryHash = EntryHashGenerator.generate(entryJson, scid);

        // Step 8: Set versionId to "1-<entryHash>"
        entry.setVersionId("1-" + entryHash);

        // Step 9-10: Generate and attach proof
        DataIntegrityProof proof = ProofGenerator.generate(config.getSigner(), entry);
        entry.setProof(Collections.singletonList(proof));

        // Build result
        String did = didTemplate.replace(SCID_PLACEHOLDER, scid);
        String logLine = entry.toJsonLine();
        return new CreateDidResult(did, entry, logLine);
    }

    private static void validate(CreateDidConfig config) {
        if (config.getDomain() == null || config.getDomain().isEmpty()) {
            throw new ValidationException("domain is required");
        }
        if (config.getSigner() == null) {
            throw new ValidationException("signer is required");
        }
        if (config.getTtl() != null && config.getTtl() < 0) {
            throw new ValidationException(
                    "ttl must be non-negative, was " + config.getTtl());
        }
        if (config.getNextKeyHashes() != null) {
            for (String hash : config.getNextKeyHashes()) {
                if (hash == null || hash.isEmpty()) {
                    throw new ValidationException(
                            "nextKeyHashes entries must be non-empty");
                }
            }
        }
    }

    /** Build {@code did:webvh:{SCID}:<domain>[:<path>]}. */
    private static String buildDidTemplate(String domain, String path) {
        StringBuilder sb = new StringBuilder("did:webvh:")
                .append(SCID_PLACEHOLDER)
                .append(':')
                .append(domain);
        if (path != null && !path.isEmpty()) {
            sb.append(':').append(path);
        }
        return sb.toString();
    }

    private static JsonObject buildDidDocument(String didTemplate,
                                               String signerMultikey,
                                               CreateDidConfig config) {
        JsonObject doc = new JsonObject();
        doc.addProperty("@context", "https://www.w3.org/ns/did/v1");
        doc.addProperty("id", didTemplate);

        // Controller is optional per DID Core §5.1.2. Historical default is the DID
        // itself (preserved when the caller doesn't set controllers explicitly).
        addControllerProperty(doc, didTemplate, config.getControllers());

        // Verification method from signer
        JsonObject vm = new JsonObject();
        vm.addProperty("id", didTemplate + "#" + signerMultikey);
        vm.addProperty("type", "Multikey");
        vm.addProperty("controller", didTemplate);
        vm.addProperty("publicKeyMultibase", signerMultikey);

        JsonArray vmArray = new JsonArray();
        vmArray.add(vm);
        doc.add("verificationMethod", vmArray);

        // Authentication, assertionMethod reference the key
        String keyRef = didTemplate + "#" + signerMultikey;
        JsonArray authArray = new JsonArray();
        authArray.add(keyRef);
        doc.add("authentication", authArray);
        doc.add("assertionMethod", authArray.deepCopy());

        // Also known as
        if (config.getAlsoKnownAs() != null && !config.getAlsoKnownAs().isEmpty()) {
            JsonArray aka = new JsonArray();
            for (String alias : config.getAlsoKnownAs()) {
                aka.add(alias);
            }
            doc.add("alsoKnownAs", aka);
        }

        // Merge additional document content (services, extra verification methods, etc.)
        if (config.getAdditionalDocumentContent() != null) {
            for (Map.Entry<String, com.google.gson.JsonElement> entry
                    : config.getAdditionalDocumentContent().entrySet()) {
                // Don't override id or controller
                if (!"id".equals(entry.getKey()) && !"controller".equals(entry.getKey())) {
                    doc.add(entry.getKey(), entry.getValue().deepCopy());
                }
            }
        }

        return doc;
    }

    private static void addControllerProperty(JsonObject doc, String didTemplate,
                                               List<String> controllers) {
        if (controllers == null) {
            // Backward-compatible default: single controller equal to the DID.
            doc.addProperty("controller", didTemplate);
            return;
        }
        if (controllers.isEmpty()) {
            // Explicit opt-out: omit the controller property entirely.
            return;
        }
        if (controllers.size() == 1) {
            doc.addProperty("controller", controllers.get(0));
            return;
        }
        JsonArray arr = new JsonArray();
        for (String c : controllers) {
            arr.add(c);
        }
        doc.add("controller", arr);
    }

    private static Parameters buildParameters(String signerMultikey,
                                              CreateDidConfig config) {
        Parameters params = new Parameters()
                .setMethod(METHOD_VERSION)
                .setScid(SCID_PLACEHOLDER)
                .setUpdateKeys(Collections.singletonList(signerMultikey));

        if (config.getPortable() != null) {
            params.setPortable(config.getPortable());
        }
        if (config.getTtl() != null) {
            params.setTtl(config.getTtl());
        }
        if (config.getWitness() != null) {
            params.setWitness(config.getWitness());
        }
        if (config.getWatchers() != null) {
            params.setWatchers(config.getWatchers());
        }
        if (config.getNextKeyHashes() != null) {
            params.setNextKeyHashes(config.getNextKeyHashes());
        }
        return params;
    }

    /**
     * Extract the multikey from a Signer's verification method URI.
     * {@code did:key:z6Mk...#z6Mk...} → {@code z6Mk...}
     */
    static String extractMultikey(Signer signer) {
        String vm = signer.verificationMethod();
        int hash = vm.indexOf('#');
        if (hash >= 0) {
            return vm.substring(hash + 1);
        }
        String prefix = "did:key:";
        if (vm.startsWith(prefix)) {
            return vm.substring(prefix.length());
        }
        throw new ValidationException(
                "Cannot extract multikey from signer verificationMethod: " + vm);
    }
}
