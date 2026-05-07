package io.github.decentralizedidentity.didwebvh.core.resolve;

import com.google.gson.JsonSyntaxException;
import io.github.decentralizedidentity.didwebvh.core.ResolutionException;
import io.github.decentralizedidentity.didwebvh.core.ValidationException;
import io.github.decentralizedidentity.didwebvh.core.model.DidDocument;
import io.github.decentralizedidentity.didwebvh.core.model.JsonSupport;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.model.ResolutionMetadata;
import io.github.decentralizedidentity.didwebvh.core.model.ResolveResult;
import io.github.decentralizedidentity.didwebvh.core.url.DidWebVhUrl;
import io.github.decentralizedidentity.didwebvh.core.validate.LogChainValidator;
import io.github.decentralizedidentity.didwebvh.core.validate.ValidationResult;
import io.github.decentralizedidentity.didwebvh.core.validate.WitnessValidationResult;
import io.github.decentralizedidentity.didwebvh.core.validate.WitnessValidator;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessProofCollection;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessProofEntry;
import com.google.gson.reflect.TypeToken;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Parses, validates, and selects entries from a did:webvh log. */
class LogProcessor {

    private final LogChainValidator logChainValidator;
    private final WitnessValidator witnessValidator;

    LogProcessor() {
        this(new LogChainValidator(), new WitnessValidator());
    }

    LogProcessor(LogChainValidator logChainValidator, WitnessValidator witnessValidator) {
        this.logChainValidator = logChainValidator;
        this.witnessValidator = witnessValidator;
    }

    ResolveResult process(String didLogContent, String witnessContent,
                          String did, ResolveOptions options) {
        return process(didLogContent, witnessContent, did, options, null);
    }

    ResolveResult process(String didLogContent, String witnessContent,
                          String did, ResolveOptions options,
                          Supplier<String> witnessSupplier) {
        ResolveOptions effectiveOptions = options == null ? ResolveOptions.defaults() : options;
        validateVersionSelector(effectiveOptions);
        List<LogEntry> entries = parseEntries(didLogContent);
        String expectedDid = baseDid(did);

        ValidationResult validation = logChainValidator.validate(entries, expectedDid);
        if (!validation.isValid()) {
            throw invalidDid("Invalid DID log: " + validation.getFailureReason());
        }

        validateWitnessesIfConfigured(entries, witnessContent, witnessSupplier);

        int selectedIndex = selectEntry(entries, effectiveOptions);
        LogEntry selected = entries.get(selectedIndex);
        Parameters selectedParameters = parametersAt(entries, selectedIndex);
        ResolutionMetadata metadata = metadata(entries, selected, selectedParameters);

        ResolveResult result = new ResolveResult().setMetadata(metadata);
        if (!Boolean.TRUE.equals(selectedParameters.getDeactivated())) {
            result.setDidDocument(new DidDocument(selected.getState().deepCopy()));
        }
        return result;
    }

    private List<LogEntry> parseEntries(String didLogContent) {
        if (didLogContent == null || didLogContent.trim().isEmpty()) {
            throw invalidDid("DID log must not be empty");
        }
        String[] lines = didLogContent.split("\\R");
        List<LogEntry> entries = new ArrayList<>();
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            try {
                entries.add(LogEntry.fromJsonLine(line));
            } catch (JsonSyntaxException | ValidationException e) {
                throw new ResolutionException("Unable to parse DID log entry: "
                        + e.getMessage(), "invalidDid", e);
            }
        }
        if (entries.isEmpty()) {
            throw invalidDid("DID log must contain at least one entry");
        }
        return entries;
    }

    private String baseDid(String did) {
        if (did == null) {
            return null;
        }
        return DidWebVhUrl.parse(did).toBaseDid();
    }

    private void validateVersionSelector(ResolveOptions options) {
        if (options.hasMultipleVersionSelectors()) {
            throw invalidDid("Only one of versionId, versionTime, and versionNumber may be used");
        }
    }

    private void validateWitnessesIfConfigured(List<LogEntry> entries, String witnessContent,
                                               Supplier<String> witnessSupplier) {
        if (!requiresWitnesses(entries)) {
            return;
        }
        if (witnessContent == null || witnessContent.trim().isEmpty()) {
            witnessContent = witnessSupplier == null ? null : witnessSupplier.get();
        }
        if (witnessContent == null || witnessContent.trim().isEmpty()) {
            throw invalidDid("Witness proofs are required but were not provided");
        }

        WitnessProofCollection proofs;
        try {
            List<WitnessProofEntry> entries2 = JsonSupport.compact().fromJson(witnessContent,
                    new TypeToken<List<WitnessProofEntry>>() { }.getType());
            proofs = new WitnessProofCollection(entries2);
        } catch (JsonSyntaxException e) {
            throw new ResolutionException("Unable to parse witness proofs: "
                    + e.getMessage(), "invalidDid", e);
        }

        WitnessValidationResult result = witnessValidator.validate(entries, proofs, 0);
        if (!result.isValid()) {
            throw invalidDid("Invalid witness proofs: " + result.getFailureReason());
        }
    }

    private boolean requiresWitnesses(List<LogEntry> entries) {
        Parameters active = Parameters.defaults();
        for (LogEntry entry : entries) {
            active = active.merge(entry.getParameters());
            if (active.getWitness() != null && active.getWitness().isActive()) {
                return true;
            }
        }
        return false;
    }

    private int selectEntry(List<LogEntry> entries, ResolveOptions options) {
        if (options.getVersionId() != null) {
            for (int i = 0; i < entries.size(); i++) {
                if (options.getVersionId().equals(entries.get(i).getVersionId())) {
                    return i;
                }
            }
            throw invalidDid("No DID log entry found for versionId "
                    + options.getVersionId());
        }
        if (options.getVersionNumber() != null) {
            for (int i = 0; i < entries.size(); i++) {
                if (options.getVersionNumber().intValue() == entries.get(i).getVersionNumber()) {
                    return i;
                }
            }
            throw invalidDid("No DID log entry found for versionNumber "
                    + options.getVersionNumber());
        }
        if (options.getVersionTime() != null) {
            return selectByTime(entries, options.getVersionTime());
        }
        return entries.size() - 1;
    }

    private int selectByTime(List<LogEntry> entries, String versionTime) {
        Instant requested;
        try {
            requested = Instant.parse(versionTime);
        } catch (DateTimeParseException e) {
            throw new ResolutionException("Invalid versionTime: " + versionTime,
                    "invalidDid", e);
        }

        int selected = -1;
        for (int i = 0; i < entries.size(); i++) {
            Instant entryTime = Instant.parse(entries.get(i).getVersionTime());
            if (!entryTime.isAfter(requested)) {
                selected = i;
            }
        }
        if (selected < 0) {
            throw invalidDid("No DID log entry found at or before versionTime "
                    + versionTime);
        }
        return selected;
    }

    private Parameters parametersAt(List<LogEntry> entries, int selectedIndex) {
        Parameters active = Parameters.defaults();
        for (int i = 0; i <= selectedIndex; i++) {
            active = active.merge(entries.get(i).getParameters());
        }
        return active;
    }

    private ResolutionMetadata metadata(List<LogEntry> entries, LogEntry selected,
                                        Parameters parameters) {
        return new ResolutionMetadata()
                .setVersionId(selected.getVersionId())
                .setVersionTime(selected.getVersionTime())
                .setCreated(entries.get(0).getVersionTime())
                .setUpdated(selected.getVersionTime())
                .setScid(parameters.getScid())
                .setPortable(parameters.getPortable())
                .setDeactivated(parameters.getDeactivated())
                .setTtl(parameters.getTtl() == null ? null : parameters.getTtl().toString())
                .setWitness(parameters.getWitness())
                .setWatchers(parameters.getWatchers());
    }

    private ResolutionException invalidDid(String message) {
        return new ResolutionException(message, "invalidDid");
    }
}
