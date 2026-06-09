package io.github.decentralizedidentity.didwebvh.core.validate;

import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.DidWebVh;
import io.github.decentralizedidentity.didwebvh.core.create.CreateDidResult;
import io.github.decentralizedidentity.didwebvh.core.model.DataIntegrityProof;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.signing.Signer;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessEntry;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessProofCollection;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessProofEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WitnessValidatorTest {

    private static Signer authorSigner;
    private static Signer witnessSigner1;
    private static Signer witnessSigner2;
    private static String witnessDid1;
    private static String witnessDid2;
    private static WitnessValidator validator;

    @BeforeAll
    static void setUp() {
        authorSigner = LogChainValidatorTest.makeTestSigner();
        witnessSigner1 = LogChainValidatorTest.makeTestSigner();
        witnessSigner2 = LogChainValidatorTest.makeTestSigner();
        witnessDid1 = "did:key:" + LogChainValidatorTest.extractMultikey(
                witnessSigner1.verificationMethod());
        witnessDid2 = "did:key:" + LogChainValidatorTest.extractMultikey(
                witnessSigner2.verificationMethod());
        validator = new WitnessValidator();
    }

    private WitnessProofCollection makeWitnessProofs(String versionId,
                                                     Signer... signers) {
        JsonObject doc = new JsonObject();
        doc.addProperty("versionId", versionId);

        DataIntegrityProof[] proofs = new DataIntegrityProof[signers.length];
        for (int i = 0; i < signers.length; i++) {
            proofs[i] = LogChainValidatorTest.signDocument(signers[i], doc);
        }

        WitnessProofEntry entry = new WitnessProofEntry(versionId, Arrays.asList(proofs));
        return new WitnessProofCollection(Collections.singletonList(entry));
    }

    private CreateDidResult createWithWitness(int threshold, WitnessEntry... witnesses) {
        WitnessConfig wc = new WitnessConfig(threshold, Arrays.asList(witnesses));
        return DidWebVh.create("example.com", authorSigner).witness(wc).execute();
    }

    @Test
    void validSingleWitnessProof() {
        CreateDidResult create = createWithWitness(1,
                new WitnessEntry(witnessDid1));
        LogEntry entry = create.getLogEntry();

        WitnessProofCollection proofs = makeWitnessProofs(entry.getVersionId(), witnessSigner1);
        WitnessValidationResult result = validator.validate(
                Collections.singletonList(entry), proofs, 0);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validTwoOfTwoWitnessProofs() {
        CreateDidResult create = createWithWitness(2,
                new WitnessEntry(witnessDid1),
                new WitnessEntry(witnessDid2));
        LogEntry entry = create.getLogEntry();

        WitnessProofCollection proofs = makeWitnessProofs(
                entry.getVersionId(), witnessSigner1, witnessSigner2);
        WitnessValidationResult result = validator.validate(
                Collections.singletonList(entry), proofs, 0);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validOneOfTwoThresholdProofs() {
        CreateDidResult create = createWithWitness(1,
                new WitnessEntry(witnessDid1),
                new WitnessEntry(witnessDid2));
        LogEntry entry = create.getLogEntry();

        // Only one proof provided but threshold is 1
        WitnessProofCollection proofs = makeWitnessProofs(
                entry.getVersionId(), witnessSigner1);
        WitnessValidationResult result = validator.validate(
                Collections.singletonList(entry), proofs, 0);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void insufficientWitnessProofsFails() {
        CreateDidResult create = createWithWitness(2,
                new WitnessEntry(witnessDid1),
                new WitnessEntry(witnessDid2));
        LogEntry entry = create.getLogEntry();

        // Only one proof but threshold is 2
        WitnessProofCollection proofs = makeWitnessProofs(
                entry.getVersionId(), witnessSigner1);
        WitnessValidationResult result = validator.validate(
                Collections.singletonList(entry), proofs, 0);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailedEntryIndex()).isEqualTo(0);
        assertThat(result.getFailureReason()).contains("insufficient");
    }

    @Test
    void missingWitnessProofEntryFails() {
        CreateDidResult create = createWithWitness(1, new WitnessEntry(witnessDid1));
        LogEntry entry = create.getLogEntry();

        // Provide proof for a different versionId
        WitnessProofCollection proofs = makeWitnessProofs("99-wronghash", witnessSigner1);
        WitnessValidationResult result = validator.validate(
                Collections.singletonList(entry), proofs, 0);

        assertThat(result.isValid()).isFalse();
        // Spec §3.7.8: proofs whose versionId is not in the published log are
        // ignored. With no usable proofs, the threshold is unmet.
        assertThat(result.getFailureReason()).contains("insufficient witness proofs");
    }

    @Test
    void unknownWitnessProofIgnored() {
        // Proof is signed by an unauthorized signer; threshold not met
        CreateDidResult create = createWithWitness(1, new WitnessEntry(witnessDid1));
        LogEntry entry = create.getLogEntry();

        Signer stranger = LogChainValidatorTest.makeTestSigner();
        WitnessProofCollection proofs = makeWitnessProofs(
                entry.getVersionId(), stranger);
        WitnessValidationResult result = validator.validate(
                Collections.singletonList(entry), proofs, 0);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).contains("insufficient");
    }

    @Test
    void nullWitnessProofCollectionFails() {
        CreateDidResult create = createWithWitness(1, new WitnessEntry(witnessDid1));
        WitnessValidationResult result = validator.validate(
                Collections.singletonList(create.getLogEntry()), null, 0);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).contains("null");
    }

    @Test
    void noWitnessConfigSkipsValidation() {
        // Entry has no witness config → always valid
        CreateDidResult create = DidWebVh.create("example.com", authorSigner).execute();
        WitnessValidationResult result = validator.validate(
                Collections.singletonList(create.getLogEntry()),
                new WitnessProofCollection(Collections.emptyList()), 0);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void crossDidWitnessReplayRejected() {
        // negative-cross-did-witness-replay: DID-A's first entry needs a
        // witness proof. Attacker injects DID-B's genuine proof for a
        // versionId from DID-B's log; later DID-A entries turn witnessing
        // off so no later proof carries the threshold for entry 1. The
        // resolver MUST ignore proofs whose versionId is not in DID-A's
        // published log (spec §3.7.8) — leaving the threshold unmet.
        WitnessConfig wc = new WitnessConfig(1,
                Collections.singletonList(new WitnessEntry(witnessDid1)));
        CreateDidResult create = DidWebVh.create("example.com", authorSigner)
                .witness(wc)
                .execute();
        LogEntry e1 = create.getLogEntry();

        // Entry 2 disables witnessing for the remainder of the log.
        Parameters witnessOff = new Parameters().setWitness(WitnessConfig.empty());
        LogEntry e2 = LogChainValidatorTest.buildUpdateEntry(
                e1, create.getDid(), authorSigner, witnessOff, null);

        // The "lifted" proof is a real signature by the shared witness over
        // a versionId from another DID's log — i.e., a versionId NOT present
        // in this DID's log.
        WitnessProofCollection foreignProofs = makeWitnessProofs(
                "3-zForeignDidEntryHashThatDoesNotExistInThisLog", witnessSigner1);

        WitnessValidationResult result = validator.validate(
                Arrays.asList(e1, e2), foreignProofs, 0);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).contains("insufficient witness proofs");
    }

    private WitnessProofCollection makeWitnessProofs(WitnessProofEntry... entries) {
        return new WitnessProofCollection(Arrays.asList(entries));
    }

    private WitnessProofEntry makeProofEntry(String versionId, Signer... signers) {
        JsonObject doc = new JsonObject();
        doc.addProperty("versionId", versionId);
        DataIntegrityProof[] proofs = new DataIntegrityProof[signers.length];
        for (int i = 0; i < signers.length; i++) {
            proofs[i] = LogChainValidatorTest.signDocument(signers[i], doc);
        }
        return new WitnessProofEntry(versionId, Arrays.asList(proofs));
    }

    @Test
    void witnessListReductionStillRequiresPriorList() {
        // Spec §3.7.5: when entry 2 reduces the witness list from {w1,w2}@2 to
        // {w1}@1, that change takes effect only AFTER entry 2 is published, so
        // entry 2 must still be witnessed by the PRIOR list {w1,w2}@2. A pruned
        // did-witness.json with both proofs at versionId 2 satisfies both
        // entries (issue #6). See vectors/witness-update/rust.
        CreateDidResult create = createWithWitness(2,
                new WitnessEntry(witnessDid1), new WitnessEntry(witnessDid2));
        LogEntry e1 = create.getLogEntry();
        LogEntry e2 = LogChainValidatorTest.buildUpdateEntry(
                e1, create.getDid(), authorSigner,
                new Parameters().setWitness(new WitnessConfig(1,
                        Collections.singletonList(new WitnessEntry(witnessDid1)))),
                null);

        WitnessProofCollection proofs = makeWitnessProofs(
                makeProofEntry(e2.getVersionId(), witnessSigner1, witnessSigner2));

        WitnessValidationResult result = validator.validate(Arrays.asList(e1, e2), proofs, 0);
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void witnessListReductionWithOnlyNewListFails() {
        // The buggy behaviour from issue #6: entry 2 reduces to {w1}@1 and only
        // w1 provides a proof. Because entry 2 must be witnessed by the prior
        // {w1,w2}@2 list, a single proof is insufficient and resolution fails.
        CreateDidResult create = createWithWitness(2,
                new WitnessEntry(witnessDid1), new WitnessEntry(witnessDid2));
        LogEntry e1 = create.getLogEntry();
        LogEntry e2 = LogChainValidatorTest.buildUpdateEntry(
                e1, create.getDid(), authorSigner,
                new Parameters().setWitness(new WitnessConfig(1,
                        Collections.singletonList(new WitnessEntry(witnessDid1)))),
                null);

        WitnessProofCollection proofs = makeWitnessProofs(
                makeProofEntry(e2.getVersionId(), witnessSigner1));

        WitnessValidationResult result = validator.validate(Arrays.asList(e1, e2), proofs, 0);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).contains("insufficient");
    }

    @Test
    void fromEntryIndexRespected() {
        // Two entries; only second requires witnessing
        CreateDidResult create = DidWebVh.create("example.com", authorSigner).execute();
        LogEntry e1 = create.getLogEntry();

        WitnessConfig wc = new WitnessConfig(1,
                Collections.singletonList(new WitnessEntry(witnessDid1)));
        LogEntry e2 = LogChainValidatorTest.buildUpdateEntry(
                e1, create.getDid(), authorSigner,
                new Parameters().setWitness(wc),
                null);

        // Provide proof only for e2
        WitnessProofCollection proofs = makeWitnessProofs(e2.getVersionId(), witnessSigner1);
        List<LogEntry> entries = Arrays.asList(e1, e2);

        // fromEntryIndex=1 → only validate e2
        WitnessValidationResult result = validator.validate(entries, proofs, 1);
        assertThat(result.isValid()).isTrue();
    }
}
