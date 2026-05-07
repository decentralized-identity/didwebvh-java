package io.github.decentralizedidentity.didwebvh.core.integration;

import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.DidWebVh;
import io.github.decentralizedidentity.didwebvh.core.DidWebVhState;
import io.github.decentralizedidentity.didwebvh.core.create.CreateDidResult;
import io.github.decentralizedidentity.didwebvh.core.crypto.EntryHashGenerator;
import io.github.decentralizedidentity.didwebvh.core.crypto.ScidGenerator;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.signing.ProofVerifier;
import io.github.decentralizedidentity.didwebvh.core.signing.Signer;
import io.github.decentralizedidentity.didwebvh.core.url.DidToHttpsTransformer;
import io.github.decentralizedidentity.didwebvh.core.validate.ValidationResult;
import io.github.decentralizedidentity.didwebvh.core.validate.WitnessValidationResult;
import io.github.decentralizedidentity.didwebvh.core.validate.WitnessValidator;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessProofCollection;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec-compliance integration tests. Each test exercises a MUST requirement of
 * the did:webvh 1.0 spec by loading one of the committed vectors under
 * {@code src/test/resources/test-vectors/} or by running the library end-to-end.
 */
class SpecComplianceIT {

    // ---------------------------------------------------------------- //
    // Vector-based tests (fixed inputs from src/test/resources)        //
    // ---------------------------------------------------------------- //

    @Test
    void firstLogEntryGoodValidates() {
        List<LogEntry> entries = TestVectors.parseLog(
                TestVectors.readResource("first-log-entry-good.jsonl"));
        assertThat(entries).hasSize(1);

        LogEntry entry = entries.get(0);
        String scid = entry.getParameters().getScid();
        assertThat(ScidGenerator.verify(scid, entry)).isTrue();
        assertThat(EntryHashGenerator.verify(entry, scid)).isTrue();
        assertThat(ProofVerifier.verify(entry.getProof().get(0), entry)).isTrue();

        ValidationResult result = DidWebVh.validate(entries,
                entry.getState().get("id").getAsString());
        assertThat(result.isValid())
                .as("validation message: %s", result.getFailureReason())
                .isTrue();
    }

    @Test
    void firstLogEntryTamperedFailsValidation() {
        List<LogEntry> entries = TestVectors.parseLog(
                TestVectors.readResource("first-log-entry-tampered.jsonl"));

        ValidationResult result = DidWebVh.validate(entries, null);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason())
                .containsAnyOf("SCID", "entry hash");
    }

    @Test
    void multiEntryLogValidatesAndWitnessVerifies() {
        String jsonl = TestVectors.readResource("multi-entry-log.jsonl");
        List<LogEntry> entries = TestVectors.parseLog(jsonl);
        assertThat(entries).hasSizeGreaterThanOrEqualTo(3);

        ValidationResult result = DidWebVh.validate(entries,
                entries.get(0).getState().get("id").getAsString());
        assertThat(result.isValid())
                .as("validation message: %s", result.getFailureReason())
                .isTrue();

        WitnessProofCollection proofs = TestVectors.parseWitnessProofs(
                TestVectors.readResource("multi-entry-witness.json"));

        WitnessValidationResult witnessResult =
                new WitnessValidator().validate(entries, proofs, 0);
        assertThat(witnessResult.isValid())
                .as("witness message: %s", witnessResult.getFailureReason())
                .isTrue();
    }

    @Test
    void deactivatedDidEndsWithDeactivatedTrue() {
        List<LogEntry> entries = TestVectors.parseLog(
                TestVectors.readResource("deactivated-did.jsonl"));
        ValidationResult result = DidWebVh.validate(entries,
                entries.get(0).getState().get("id").getAsString());
        assertThat(result.isValid()).isTrue();
        assertThat(result.getActiveParameters().getDeactivated()).isTrue();
        assertThat(result.getActiveParameters().getUpdateKeys()).isEmpty();
    }

    @Test
    void migratedDidPreservesScidAndAddsAlsoKnownAs() {
        List<LogEntry> entries = TestVectors.parseLog(
                TestVectors.readResource("migrated-did.jsonl"));
        assertThat(entries).hasSize(2);

        String originalDid = entries.get(0).getState().get("id").getAsString();
        String migratedDid = entries.get(1).getState().get("id").getAsString();

        // SCID preserved; domain changed
        String scid = entries.get(0).getParameters().getScid();
        assertThat(originalDid).contains(scid);
        assertThat(migratedDid).contains(scid);
        assertThat(originalDid).contains(":example.com");
        assertThat(migratedDid).contains(":new.example.com");

        // alsoKnownAs must include previous DID (spec §3.7.6)
        assertThat(entries.get(1).getState().getAsJsonArray("alsoKnownAs")
                .toString()).contains(originalDid);

        // The second entry's document id is the new DID — validator tracks that
        ValidationResult result = DidWebVh.validate(entries, migratedDid);
        assertThat(result.isValid())
                .as("validation message: %s", result.getFailureReason())
                .isTrue();
    }

    @Test
    void preRotationLogEnforcesKeyHashMatch() {
        List<LogEntry> entries = TestVectors.parseLog(
                TestVectors.readResource("pre-rotation-log.jsonl"));
        assertThat(entries).hasSize(2);

        // Entry 1 committed nextKeyHashes; entry 2 rotated to a key whose hash matches.
        assertThat(entries.get(0).getParameters().getNextKeyHashes())
                .isNotEmpty();

        ValidationResult result = DidWebVh.validate(entries,
                entries.get(0).getState().get("id").getAsString());
        assertThat(result.isValid())
                .as("validation message: %s", result.getFailureReason())
                .isTrue();
    }

    @Test
    void preRotationViolationIsRejected() {
        // Tamper: replace updateKeys in entry 2 with an arbitrary new multikey
        // whose hash is NOT in entry 1's nextKeyHashes. Validator must reject.
        List<LogEntry> entries = TestVectors.parseLog(
                TestVectors.readResource("pre-rotation-log.jsonl"));

        Parameters bogus = new Parameters()
                .setUpdateKeys(Collections.singletonList(
                        "z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"))
                .setNextKeyHashes(entries.get(1).getParameters().getNextKeyHashes());
        entries.get(1).setParameters(bogus);

        ValidationResult result = DidWebVh.validate(entries, null);
        assertThat(result.isValid()).isFalse();
    }

    @Test
    void tamperingAnyEntryCausesValidationFailure() {
        // Spec MUST: any modification to a published entry invalidates the chain.
        List<LogEntry> entries = TestVectors.parseLog(
                TestVectors.readResource("multi-entry-log.jsonl"));

        JsonObject state = entries.get(1).getState();
        state.addProperty("tampered", true);

        ValidationResult result = DidWebVh.validate(entries, null);
        assertThat(result.isValid()).isFalse();
    }

    // ---------------------------------------------------------------- //
    // Parameter-rules MUST requirements                                 //
    // ---------------------------------------------------------------- //

    @Test
    void firstEntryMustSpecifyMethodAndScidAndUpdateKeys() {
        List<LogEntry> entries = TestVectors.parseLog(
                TestVectors.readResource("first-log-entry-good.jsonl"));

        entries.get(0).setParameters(new Parameters()); // strip required params
        ValidationResult result = DidWebVh.validate(entries, null);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).containsIgnoringCase("first entry");
    }

    @Test
    void scidMustNotAppearInLaterEntries() {
        Signer signer = TestVectors.seededSigner(TestVectors.AUTHOR_SEED);
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        DidWebVhState state = DidWebVhState.from(create.getDid(), create.getLogEntry());

        Parameters withScid = new Parameters()
                .setScid(create.getLogEntry().getParameters().getScid());
        LogEntry second = DidWebVh.update(state, signer)
                .changedParameters(withScid)
                .execute()
                .getNewEntries()
                .get(0);

        ValidationResult result = DidWebVh.validate(
                java.util.Arrays.asList(create.getLogEntry(), second), null);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).containsIgnoringCase("scid must only appear");
    }

    @Test
    void portableCannotBeFlippedOnAfterFirstEntry() {
        Signer signer = TestVectors.seededSigner(TestVectors.AUTHOR_SEED);
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        DidWebVhState state = DidWebVhState.from(create.getDid(), create.getLogEntry());

        Parameters flip = new Parameters().setPortable(Boolean.TRUE);
        LogEntry second = DidWebVh.update(state, signer)
                .changedParameters(flip)
                .execute()
                .getNewEntries()
                .get(0);

        ValidationResult result = DidWebVh.validate(
                java.util.Arrays.asList(create.getLogEntry(), second), null);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).containsIgnoringCase("portable");
    }

    @Test
    void versionNumbersMustStartAtOneAndIncrementByOne() {
        Signer signer = TestVectors.seededSigner(TestVectors.AUTHOR_SEED);
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();

        // Force versionId "2-<hash>" for the only entry → breaks rule.
        LogEntry entry = create.getLogEntry();
        String suffix = entry.getVersionId().substring(entry.getVersionId().indexOf('-'));
        entry.setVersionId("2" + suffix);

        ValidationResult result = DidWebVh.validate(
                Collections.singletonList(entry), null);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).containsIgnoringCase("versionNumber");
    }

    // ---------------------------------------------------------------- //
    // DID-to-HTTPS transformation (spec §3.4)                            //
    // ---------------------------------------------------------------- //

    @Test
    void didToHttpsBareDomain() {
        String scid = scidPlaceholder();
        String did = "did:webvh:" + scid + ":example.com";
        assertThat(DidToHttpsTransformer.toHttpsUrl(did))
                .isEqualTo("https://example.com/.well-known/did.jsonl");
        assertThat(DidToHttpsTransformer.toWitnessUrl(did))
                .isEqualTo("https://example.com/.well-known/did-witness.json");
    }

    @Test
    void didToHttpsWithPath() {
        String scid = scidPlaceholder();
        String did = "did:webvh:" + scid + ":example.com:dids:issuer";
        assertThat(DidToHttpsTransformer.toHttpsUrl(did))
                .isEqualTo("https://example.com/dids/issuer/did.jsonl");
    }

    @Test
    void didToHttpsWithPort() {
        String scid = scidPlaceholder();
        String did = "did:webvh:" + scid + ":example.com%3A8080:dids:issuer";
        assertThat(DidToHttpsTransformer.toHttpsUrl(did))
                .isEqualTo("https://example.com:8080/dids/issuer/did.jsonl");
    }

    @Test
    void didToHttpsPercentEncodesPathSegments() {
        String scid = scidPlaceholder();
        String did = "did:webvh:" + scid + ":example.com:a%20b";
        // Segments are already percent-encoded once; transformer must not
        // decode them, but any non-unreserved bytes still round-trip safely.
        assertThat(DidToHttpsTransformer.toHttpsUrl(did))
                .isEqualTo("https://example.com/a%2520b/did.jsonl");
    }

    @Test
    void didToDidWebStripsScid() {
        String scid = scidPlaceholder();
        String didWebVh = "did:webvh:" + scid + ":example.com:dids:issuer";
        assertThat(DidToHttpsTransformer.toDidWebUrl(didWebVh))
                .isEqualTo("did:web:example.com:dids:issuer");
    }

    // ---------------------------------------------------------------- //
    // Property-style sanity: create + many updates always validate      //
    // ---------------------------------------------------------------- //

    @Test
    void createFollowedByManyUpdatesAlwaysValidates() {
        Signer signer = TestVectors.seededSigner(TestVectors.AUTHOR_SEED);
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        DidWebVhState state = DidWebVhState.from(create.getDid(), create.getLogEntry());

        for (int i = 0; i < 8; i++) {
            JsonObject newDoc = state.getLastEntry().getState().deepCopy();
            newDoc.addProperty("iteration", i);
            DidWebVh.update(state, signer)
                    .newDocument(newDoc)
                    .execute()
                    .getNewEntries()
                    .forEach(state::appendEntry);
        }

        ValidationResult result = state.validate();
        assertThat(result.isValid())
                .as("validation message: %s", result.getFailureReason())
                .isTrue();
        assertThat(state.getLogEntries()).hasSize(9);
    }

    private static String scidPlaceholder() {
        // 46 base58btc characters — any valid-looking string works here; the
        // transformer does not validate beyond length/charset.
        return "QmZxXZFh6iZ8JL1MjgRE4banPZbWJug71NgvnE8cUSoHdc";
    }
}
