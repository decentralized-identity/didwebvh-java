package io.github.decentralizedidentity.didwebvh.core.validate;

import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.DidWebVh;
import io.github.decentralizedidentity.didwebvh.core.create.CreateDidResult;
import io.github.decentralizedidentity.didwebvh.core.crypto.Base58Btc;
import io.github.decentralizedidentity.didwebvh.core.crypto.EntryHashGenerator;

import io.github.decentralizedidentity.didwebvh.core.crypto.MultikeyUtil;
import io.github.decentralizedidentity.didwebvh.core.crypto.PreRotationHashGenerator;
import io.github.decentralizedidentity.didwebvh.core.model.DataIntegrityProof;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.signing.ProofGenerator;
import io.github.decentralizedidentity.didwebvh.core.signing.Signer;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessEntry;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogChainValidatorTest {

    private static Signer signer;
    private static LogChainValidator validator;

    @BeforeAll
    static void setUp() {
        signer = makeTestSigner();
        validator = new LogChainValidator();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    static Signer makeTestSigner() {
        Ed25519KeyPairGenerator gen = new Ed25519KeyPairGenerator();
        gen.init(new Ed25519KeyGenerationParameters(new SecureRandom()));
        AsymmetricCipherKeyPair pair = gen.generateKeyPair();
        Ed25519PrivateKeyParameters priv = (Ed25519PrivateKeyParameters) pair.getPrivate();
        Ed25519PublicKeyParameters pub = (Ed25519PublicKeyParameters) pair.getPublic();
        String mk = MultikeyUtil.encode(MultikeyUtil.ED25519_KEY_TYPE, pub.getEncoded());
        String vm = "did:key:" + mk + "#" + mk;
        return new Signer() {
            @Override public String keyType() { return "Ed25519"; }
            @Override public String verificationMethod() { return vm; }
            @Override public byte[] sign(byte[] data) {
                Ed25519Signer s = new Ed25519Signer();
                s.init(true, priv);
                s.update(data, 0, data.length);
                return s.generateSignature();
            }
        };
    }

    static String extractMultikey(String verificationMethod) {
        int hash = verificationMethod.indexOf('#');
        return hash >= 0 ? verificationMethod.substring(hash + 1)
                : verificationMethod.substring("did:key:".length());
    }

    /** Build an update entry on top of a previous entry. */
    static LogEntry buildUpdateEntry(LogEntry previous, String did, Signer updateSigner,
                                     Parameters changedParams, JsonObject newState) {
        int newVersion = previous.getVersionNumber() + 1;
        JsonObject state = newState != null ? newState : previous.getState().deepCopy();
        Parameters params = changedParams != null ? changedParams : new Parameters();

        LogEntry preliminary = new LogEntry()
                .setVersionId(previous.getVersionId())
                .setVersionTime(Instant.now().toString())
                .setParameters(params)
                .setState(state);

        String entryHash = EntryHashGenerator.generate(preliminary.toJsonLine(),
                previous.getVersionId());
        preliminary.setVersionId(newVersion + "-" + entryHash);

        DataIntegrityProof proof = ProofGenerator.generate(updateSigner, preliminary);
        return preliminary.setProof(Collections.singletonList(proof));
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void singleEntryValid() {
        CreateDidResult result = DidWebVh.create("example.com", signer).execute();
        List<LogEntry> entries = Collections.singletonList(result.getLogEntry());

        ValidationResult vr = validator.validate(entries, result.getDid());

        assertThat(vr.isValid()).isTrue();
        assertThat(vr.getLastValidEntryIndex()).isEqualTo(0);
        assertThat(vr.getFailureReason()).isNull();
    }

    @Test
    void multiEntryValid() {
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        LogEntry e1 = create.getLogEntry();

        JsonObject state2 = e1.getState().deepCopy();
        state2.addProperty("updated", "true");
        LogEntry e2 = buildUpdateEntry(e1, create.getDid(), signer, null, state2);

        JsonObject state3 = state2.deepCopy();
        state3.addProperty("updated2", "true");
        LogEntry e3 = buildUpdateEntry(e2, create.getDid(), signer, null, state3);

        List<LogEntry> entries = Arrays.asList(e1, e2, e3);
        ValidationResult vr = validator.validate(entries, create.getDid());

        assertThat(vr.isValid()).isTrue();
        assertThat(vr.getLastValidEntryIndex()).isEqualTo(2);
    }

    @Test
    void tamperedEntryHashFails() {
        // Tamper a second entry's state; first entry passes so failure is clearly entry-hash
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        LogEntry e1 = create.getLogEntry();
        LogEntry e2 = buildUpdateEntry(e1, create.getDid(), signer, null, null);

        // Modify e2 state without recalculating the hash
        JsonObject tamperedState = e2.getState().deepCopy();
        tamperedState.addProperty("attacker", "was here");
        LogEntry tampered = new LogEntry()
                .setVersionId(e2.getVersionId())
                .setVersionTime(e2.getVersionTime())
                .setParameters(e2.getParameters())
                .setState(tamperedState)
                .setProof(e2.getProof());

        ValidationResult vr = validator.validate(Arrays.asList(e1, tampered), create.getDid());

        assertThat(vr.isValid()).isFalse();
        assertThat(vr.getFailedEntryIndex()).isEqualTo(1);
        assertThat(vr.getLastValidEntryIndex()).isEqualTo(0);
        assertThat(vr.getFailureReason()).contains("entry hash");
    }

    @Test
    void tamperedProofFails() {
        // Sign different data with the authorized key → valid format, wrong signature
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        LogEntry e1 = create.getLogEntry();
        LogEntry e2 = buildUpdateEntry(e1, create.getDid(), signer, null, null);

        // Replace e2's proofValue with a signature over "WRONG_DATA"
        byte[] wrongSig = signer.sign("WRONG_DATA".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String wrongProofValue = Base58Btc.encodeMultibase(wrongSig);

        DataIntegrityProof originalProof = e2.getProof().get(0);
        DataIntegrityProof badProof = new DataIntegrityProof()
                .setType(originalProof.getType())
                .setCryptosuite(originalProof.getCryptosuite())
                .setVerificationMethod(originalProof.getVerificationMethod())
                .setProofPurpose(originalProof.getProofPurpose())
                .setCreated(originalProof.getCreated())
                .setProofValue(wrongProofValue);

        LogEntry tampered = new LogEntry()
                .setVersionId(e2.getVersionId())
                .setVersionTime(e2.getVersionTime())
                .setParameters(e2.getParameters())
                .setState(e2.getState())
                .setProof(Collections.singletonList(badProof));

        ValidationResult vr = validator.validate(Arrays.asList(e1, tampered), create.getDid());

        assertThat(vr.isValid()).isFalse();
        assertThat(vr.getFailedEntryIndex()).isEqualTo(1);
        assertThat(vr.getFailureReason()).containsIgnoringCase("proof");
    }

    @Test
    void wrongSigningKeyFails() {
        Signer unauthorizedSigner = makeTestSigner();
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        LogEntry e1 = create.getLogEntry();

        // Build update signed with unauthorized key
        LogEntry e2 = buildUpdateEntry(e1, create.getDid(), unauthorizedSigner, null, null);

        ValidationResult vr = validator.validate(Arrays.asList(e1, e2), create.getDid());

        assertThat(vr.isValid()).isFalse();
        assertThat(vr.getFailedEntryIndex()).isEqualTo(1);
        assertThat(vr.getLastValidEntryIndex()).isEqualTo(0);
        assertThat(vr.getFailureReason()).contains("updateKeys");
    }

    @Test
    void versionNumberGapFails() {
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        LogEntry e1 = create.getLogEntry();
        LogEntry e2 = buildUpdateEntry(e1, create.getDid(), signer, null, null);

        // Rename e2 to version 3 instead of 2
        String fakeVersionId = "3-" + e2.getEntryHash();
        LogEntry e2bad = new LogEntry()
                .setVersionId(fakeVersionId)
                .setVersionTime(e2.getVersionTime())
                .setParameters(e2.getParameters())
                .setState(e2.getState())
                .setProof(e2.getProof());

        ValidationResult vr = validator.validate(Arrays.asList(e1, e2bad), create.getDid());

        assertThat(vr.isValid()).isFalse();
        assertThat(vr.getFailedEntryIndex()).isEqualTo(1);
        assertThat(vr.getFailureReason()).contains("versionNumber");
    }

    @Test
    void versionTimeOrderingFails() {
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        LogEntry e1 = create.getLogEntry();

        // Build entry with versionTime before e1
        String oldTime = Instant.now().minusSeconds(3600).toString();
        LogEntry preliminary = new LogEntry()
                .setVersionId(e1.getVersionId())
                .setVersionTime(oldTime)
                .setParameters(new Parameters())
                .setState(e1.getState().deepCopy());

        String entryHash = EntryHashGenerator.generate(
                preliminary.toJsonLine(), e1.getVersionId());
        preliminary.setVersionId("2-" + entryHash);
        DataIntegrityProof proof = ProofGenerator.generate(signer, preliminary);
        LogEntry e2 = preliminary.setProof(Collections.singletonList(proof));

        ValidationResult vr = validator.validate(Arrays.asList(e1, e2), create.getDid());

        assertThat(vr.isValid()).isFalse();
        assertThat(vr.getFailureReason()).contains("versionTime");
    }

    @Test
    void scidTamperingFails() {
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        LogEntry original = create.getLogEntry();

        // Replace SCID in parameters with a different value
        Parameters tamperedParams = original.getParameters().merge(
                new Parameters().setScid("zFakeSCIDXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"));
        LogEntry tampered = new LogEntry()
                .setVersionId(original.getVersionId())
                .setVersionTime(original.getVersionTime())
                .setParameters(tamperedParams)
                .setState(original.getState())
                .setProof(original.getProof());

        ValidationResult vr = validator.validate(
                Collections.singletonList(tampered), create.getDid());

        assertThat(vr.isValid()).isFalse();
        assertThat(vr.getFailureReason()).containsIgnoringCase("SCID");
    }

    @Test
    void preRotationValidChain() {
        // Create with nextKeyHashes pointing to a second key
        Signer signer2 = makeTestSigner();
        String multikey2 = extractMultikey(signer2.verificationMethod());
        String hash2 = PreRotationHashGenerator.generateHash(multikey2);

        CreateDidResult create = DidWebVh.create("example.com", signer)
                .nextKeyHashes(Collections.singletonList(hash2))
                .execute();
        LogEntry e1 = create.getLogEntry();

        // Rotation is signed by the OLD signer (still active); it commits new key.
        Parameters rotateParams = new Parameters()
                .setUpdateKeys(Collections.singletonList(multikey2));
        LogEntry e2 = buildUpdateEntry(e1, create.getDid(), signer, rotateParams, null);

        ValidationResult vr = validator.validate(Arrays.asList(e1, e2), create.getDid());
        assertThat(vr.isValid()).isTrue();
        assertThat(vr.getActiveParameters().getUpdateKeys())
                .containsExactly(multikey2);
    }

    @Test
    void preRotationWrongKeyFails() {
        Signer signer2 = makeTestSigner();
        String hash2 = PreRotationHashGenerator.generateHash(
                extractMultikey(signer2.verificationMethod()));

        CreateDidResult create = DidWebVh.create("example.com", signer)
                .nextKeyHashes(Collections.singletonList(hash2))
                .execute();
        LogEntry e1 = create.getLogEntry();

        // Try to rotate to a different key (not the committed one)
        Signer signer3 = makeTestSigner();
        String multikey3 = extractMultikey(signer3.verificationMethod());
        Parameters wrongRotate = new Parameters()
                .setUpdateKeys(Collections.singletonList(multikey3));
        // Sign with signer (still the active key) to get past auth check
        LogEntry e2 = buildUpdateEntry(e1, create.getDid(), signer, wrongRotate, null);

        ValidationResult vr = validator.validate(Arrays.asList(e1, e2), create.getDid());

        assertThat(vr.isValid()).isFalse();
        assertThat(vr.getFailureReason()).contains("nextKeyHashes");
    }

    @Test
    void deactivationStopsChain() {
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        LogEntry e1 = create.getLogEntry();

        // Deactivate
        Parameters deactParams = new Parameters().setDeactivated(true);
        LogEntry e2 = buildUpdateEntry(e1, create.getDid(), signer, deactParams, null);

        ValidationResult vr2 = validator.validate(Arrays.asList(e1, e2), create.getDid());
        assertThat(vr2.isValid()).isTrue();
        assertThat(vr2.getActiveParameters().getDeactivated()).isTrue();

        // Entry after deactivation
        LogEntry e3 = buildUpdateEntry(e2, create.getDid(), signer, null, null);
        ValidationResult vr3 = validator.validate(Arrays.asList(e1, e2, e3), create.getDid());

        assertThat(vr3.isValid()).isFalse();
        assertThat(vr3.getFailedEntryIndex()).isEqualTo(2);
        assertThat(vr3.getLastValidEntryIndex()).isEqualTo(1);
    }

    @Test
    void missingMethodInFirstEntryFails() {
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        LogEntry original = create.getLogEntry();

        // Strip method from parameters
        Parameters noMethod = new Parameters()
                .setScid(original.getParameters().getScid())
                .setUpdateKeys(original.getParameters().getUpdateKeys());

        LogEntry bad = new LogEntry()
                .setVersionId(original.getVersionId())
                .setVersionTime(original.getVersionTime())
                .setParameters(noMethod)
                .setState(original.getState())
                .setProof(original.getProof());

        ValidationResult vr = validator.validate(Collections.singletonList(bad), create.getDid());

        assertThat(vr.isValid()).isFalse();
        assertThat(vr.getFailureReason()).contains("method");
    }

    @Test
    void scidInSecondEntryFails() {
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        LogEntry e1 = create.getLogEntry();

        // Include scid in second entry parameters (not allowed)
        Parameters badParams = new Parameters().setScid("zSomeSCID");
        LogEntry e2 = buildUpdateEntry(e1, create.getDid(), signer, badParams, null);

        ValidationResult vr = validator.validate(Arrays.asList(e1, e2), create.getDid());

        assertThat(vr.isValid()).isFalse();
        assertThat(vr.getFailureReason()).contains("scid");
    }

    @Test
    void witnessBadThresholdFails() {
        // threshold > witness count
        WitnessConfig badWitness = new WitnessConfig(3,
                Collections.singletonList(new WitnessEntry("did:key:z6MkWit")));

        CreateDidResult create = DidWebVh.create("example.com", signer)
                .witness(badWitness)
                .execute();

        ValidationResult vr = validator.validate(
                Collections.singletonList(create.getLogEntry()), create.getDid());

        assertThat(vr.isValid()).isFalse();
        assertThat(vr.getFailureReason()).containsIgnoringCase("threshold");
    }

    @Test
    void emptyLogFails() {
        ValidationResult vr = validator.validate(Collections.emptyList(), "did:webvh:test");
        assertThat(vr.isValid()).isFalse();
        assertThat(vr.getFailureReason()).contains("empty");
    }

    @Test
    void didDocumentIdMismatchFails() {
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        ValidationResult vr = validator.validate(
                Collections.singletonList(create.getLogEntry()),
                "did:webvh:zzz:wrong.com");

        assertThat(vr.isValid()).isFalse();
        assertThat(vr.getFailureReason()).contains("expectedDid");
    }

    @Test
    void nullExpectedDidSkipsIdCheck() {
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        // Passing null should skip the did-match check
        ValidationResult vr = validator.validate(
                Collections.singletonList(create.getLogEntry()), null);

        assertThat(vr.isValid()).isTrue();
    }

    @Test
    void gracefulDegradation() {
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        LogEntry e1 = create.getLogEntry();
        LogEntry e2 = buildUpdateEntry(e1, create.getDid(), signer, null, null);

        // Tamper e3 entry hash
        LogEntry e3valid = buildUpdateEntry(e2, create.getDid(), signer, null, null);
        JsonObject tamperedState = e3valid.getState().deepCopy();
        tamperedState.addProperty("bad", "data");
        LogEntry e3 = new LogEntry()
                .setVersionId(e3valid.getVersionId())
                .setVersionTime(e3valid.getVersionTime())
                .setParameters(e3valid.getParameters())
                .setState(tamperedState)
                .setProof(e3valid.getProof());

        ValidationResult vr = validator.validate(Arrays.asList(e1, e2, e3), create.getDid());

        assertThat(vr.isValid()).isFalse();
        assertThat(vr.getLastValidEntryIndex()).isEqualTo(1);
        assertThat(vr.getFailedEntryIndex()).isEqualTo(2);
    }

    @Test
    void activeParametersAccumulatedCorrectly() {
        CreateDidResult create = DidWebVh.create("example.com", signer)
                .ttl(600)
                .execute();
        LogEntry e1 = create.getLogEntry();

        // e2 updates ttl to 1200
        Parameters ttlChange = new Parameters().setTtl(1200);
        LogEntry e2 = buildUpdateEntry(e1, create.getDid(), signer, ttlChange, null);

        ValidationResult vr = validator.validate(Arrays.asList(e1, e2), create.getDid());

        assertThat(vr.isValid()).isTrue();
        assertThat(vr.getActiveParameters().getTtl()).isEqualTo(1200);
    }

    @Test
    void defaultTtlAppliedWhenNotSetInLog() {
        // No explicit ttl in any entry; validator should return default 3600
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();

        ValidationResult vr = validator.validate(
                Collections.singletonList(create.getLogEntry()), create.getDid());

        assertThat(vr.isValid()).isTrue();
        assertThat(vr.getActiveParameters().getTtl()).isEqualTo(3600);
    }

    @Test
    void specDefaultsReturnedWhenNotSetInLog() {
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        ValidationResult vr = validator.validate(
                Collections.singletonList(create.getLogEntry()), create.getDid());

        assertThat(vr.isValid()).isTrue();
        Parameters active = vr.getActiveParameters();
        assertThat(active.getPortable()).isFalse();
        assertThat(active.getDeactivated()).isFalse();
        assertThat(active.getWatchers()).isEmpty();
        assertThat(active.getWitness()).isNotNull();
        assertThat(active.getWitness().isActive()).isFalse();
    }

    // ── helper: make a proof over a JsonObject (for witness tests) ────────────

    static DataIntegrityProof signDocument(Signer s, JsonObject doc) {
        DataIntegrityProof proof = DataIntegrityProof.defaults()
                .setVerificationMethod(s.verificationMethod())
                .setCreated(Instant.now().toString());
        byte[] hashData = io.github.decentralizedidentity.didwebvh.core.signing.ProofGenerator
                .buildHashData(proof, doc);
        byte[] signature = s.sign(hashData);
        return proof.setProofValue(Base58Btc.encodeMultibase(signature));
    }

    @Test
    void noProofFails() {
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        LogEntry noProof = new LogEntry()
                .setVersionId(create.getLogEntry().getVersionId())
                .setVersionTime(create.getLogEntry().getVersionTime())
                .setParameters(create.getLogEntry().getParameters())
                .setState(create.getLogEntry().getState());

        ValidationResult vr = validator.validate(Collections.singletonList(noProof), create.getDid());

        assertThat(vr.isValid()).isFalse();
        assertThat(vr.getFailureReason()).contains("proof");
    }
}
