package io.github.decentralizedidentity.didwebvh.core.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.DidWebVh;
import io.github.decentralizedidentity.didwebvh.core.DidWebVhState;
import io.github.decentralizedidentity.didwebvh.core.ValidationException;
import io.github.decentralizedidentity.didwebvh.core.create.CreateDidResult;
import io.github.decentralizedidentity.didwebvh.core.crypto.MultikeyUtil;
import io.github.decentralizedidentity.didwebvh.core.crypto.PreRotationHashGenerator;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.signing.Signer;
import io.github.decentralizedidentity.didwebvh.core.validate.ValidationResult;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateDidOperationTest {

    private static Signer signerA;
    private static Signer signerB;
    private static String multikeyB;

    @BeforeAll
    static void setUp() {
        signerA = makeSigner();
        AsymmetricCipherKeyPair pairB = generateKeyPair();
        Ed25519PublicKeyParameters pubB =
                (Ed25519PublicKeyParameters) pairB.getPublic();
        Ed25519PrivateKeyParameters privB =
                (Ed25519PrivateKeyParameters) pairB.getPrivate();
        multikeyB = MultikeyUtil.encode(MultikeyUtil.ED25519_KEY_TYPE, pubB.getEncoded());
        String vmB = "did:key:" + multikeyB + "#" + multikeyB;
        signerB = new Signer() {
            @Override
            public String keyType() {
                return "Ed25519";
            }

            @Override
            public String verificationMethod() {
                return vmB;
            }

            @Override
            public byte[] sign(byte[] data) {
                Ed25519Signer s = new Ed25519Signer();
                s.init(true, privB);
                s.update(data, 0, data.length);
                return s.generateSignature();
            }
        };
    }

    // -------------------------------------------------------------------------
    // Simple update
    // -------------------------------------------------------------------------

    @Test
    void simpleUpdate_addService_chainValidates() {
        DidWebVhState state = createState("example.com", signerA);

        JsonObject newDoc = addService(state.getLastEntry().getState().deepCopy());
        UpdateDidResult result = DidWebVh.update(state, signerA)
                .newDocument(newDoc)
                .execute();

        assertThat(result.getNewEntries()).hasSize(1);
        LogEntry updated = result.getLogEntry();
        assertThat(updated.getVersionNumber()).isEqualTo(2);

        state.appendEntry(updated);
        ValidationResult vr = DidWebVh.validate(state.getLogEntries(), state.getDid());
        assertThat(vr.isValid()).isTrue();
        assertThat(vr.getLastValidEntryIndex()).isEqualTo(1);
    }

    @Test
    void multipleUpdates_chainValidates() {
        DidWebVhState state = createState("example.com", signerA);

        for (int i = 0; i < 5; i++) {
            UpdateDidResult result = DidWebVh.update(state, signerA).execute();
            state.appendEntry(result.getLogEntry());
        }

        ValidationResult vr = DidWebVh.validate(state.getLogEntries(), state.getDid());
        assertThat(vr.isValid()).isTrue();
        assertThat(state.getLogEntries()).hasSize(6);
    }

    // -------------------------------------------------------------------------
    // Parameter update
    // -------------------------------------------------------------------------

    @Test
    void parameterUpdate_changeTtl_chainValidates() {
        DidWebVhState state = createState("example.com", signerA);

        Parameters changed = new Parameters().setTtl(7200);
        UpdateDidResult result = DidWebVh.update(state, signerA)
                .changedParameters(changed)
                .execute();
        state.appendEntry(result.getLogEntry());

        ValidationResult vr = DidWebVh.validate(state.getLogEntries(), state.getDid());
        assertThat(vr.isValid()).isTrue();
        assertThat(vr.getActiveParameters().getTtl()).isEqualTo(7200);
    }

    // -------------------------------------------------------------------------
    // Key rotation
    // -------------------------------------------------------------------------

    @Test
    void keyRotation_oldKeyCannotSignSubsequentEntry() {
        DidWebVhState state = createState("example.com", signerA);

        // Rotate to signerB
        Parameters rotateParams = new Parameters()
                .setUpdateKeys(Collections.singletonList(multikeyB));
        UpdateDidResult rotated = DidWebVh.update(state, signerA)
                .changedParameters(rotateParams)
                .execute();
        state.appendEntry(rotated.getLogEntry());

        // Valid up to here
        ValidationResult vr = DidWebVh.validate(state.getLogEntries(), state.getDid());
        assertThat(vr.isValid()).isTrue();

        // Old key (signerA) can no longer sign valid entries
        UpdateDidResult invalidEntry = DidWebVh.update(state, signerA).execute();
        state.appendEntry(invalidEntry.getLogEntry());

        ValidationResult vr2 = DidWebVh.validate(state.getLogEntries(), state.getDid());
        assertThat(vr2.isValid()).isFalse();
        assertThat(vr2.getFailedEntryIndex()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // Validation guards
    // -------------------------------------------------------------------------

    @Test
    void updateAfterDeactivation_throws() {
        DidWebVhState state = createState("example.com", signerA);
        UpdateDidResult deactivation = DidWebVh.deactivate(state, signerA).execute();
        for (LogEntry e : deactivation.getNewEntries()) {
            state.appendEntry(e);
        }

        assertThatThrownBy(() -> DidWebVh.update(state, signerA).execute())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("deactivated");
    }

    // -------------------------------------------------------------------------
    // DidWebVhState helpers
    // -------------------------------------------------------------------------

    @Test
    void stateToDidLog_roundTrip() {
        DidWebVhState state = createState("example.com", signerA);
        UpdateDidResult result = DidWebVh.update(state, signerA).execute();
        state.appendEntry(result.getLogEntry());

        String jsonl = state.toDidLog();
        DidWebVhState reloaded = DidWebVhState.fromDidLog(state.getDid(), jsonl);
        assertThat(reloaded.getLogEntries()).hasSize(2);
        assertThat(reloaded.getDid()).isEqualTo(state.getDid());
    }

    @Test
    void stateToJson_roundTrip() {
        DidWebVhState state = createState("example.com", signerA);
        UpdateDidResult result = DidWebVh.update(state, signerA).execute();
        state.appendEntry(result.getLogEntry());

        String json = state.toJson();
        DidWebVhState reloaded = DidWebVhState.fromJson(json);
        assertThat(reloaded.getLogEntries()).hasSize(2);
        assertThat(reloaded.getDid()).isEqualTo(state.getDid());
    }

    @Test
    void stateValidate_setsActiveParameters() {
        DidWebVhState state = createState("example.com", signerA);
        ValidationResult vr = state.validate();
        assertThat(vr.isValid()).isTrue();
        assertThat(state.isValidated()).isTrue();
        assertThat(state.getActiveParameters()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Migration
    // -------------------------------------------------------------------------

    @Test
    void migration_portable_rewritesDid_addsAlsoKnownAs() {
        DidWebVhState state = createPortableState("old.example.com", signerA);
        String oldDid = state.getDid();

        UpdateDidResult result = DidWebVh.migrate(state, signerA, "new.example.com")
                .execute();
        assertThat(result.getNewEntries()).hasSize(1);

        state.appendEntry(result.getLogEntry());
        ValidationResult vr = DidWebVh.validate(state.getLogEntries(), state.getDid());
        assertThat(vr.isValid()).isTrue();

        // Document id should now contain the new domain
        JsonObject newDoc = state.getLastEntry().getState();
        String newId = newDoc.get("id").getAsString();
        assertThat(newId).contains("new.example.com");
        assertThat(newId).doesNotContain("old.example.com");

        // Old DID should be in alsoKnownAs
        com.google.gson.JsonElement aka = newDoc.get("alsoKnownAs");
        assertThat(aka).isNotNull();
        assertThat(aka.toString()).contains(oldDid);
    }

    @Test
    void migration_notPortable_throws() {
        DidWebVhState state = createState("example.com", signerA);

        assertThatThrownBy(() -> DidWebVh.migrate(state, signerA, "new.example.com")
                .execute())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("portable");
    }

    // -------------------------------------------------------------------------
    // Deactivation
    // -------------------------------------------------------------------------

    @Test
    void deactivation_setsDeactivatedFlag() {
        DidWebVhState state = createState("example.com", signerA);

        UpdateDidResult result = DidWebVh.deactivate(state, signerA).execute();
        assertThat(result.getNewEntries()).hasSize(1);

        LogEntry entry = result.getLogEntry();
        assertThat(entry.getParameters().getDeactivated()).isTrue();
        assertThat(entry.getParameters().getUpdateKeys()).isEmpty();

        state.appendEntry(entry);
        ValidationResult vr = DidWebVh.validate(state.getLogEntries(), state.getDid());
        assertThat(vr.isValid()).isTrue();
        assertThat(vr.getActiveParameters().getDeactivated()).isTrue();
    }

    @Test
    void deactivation_withPreRotation_producesTwoEntries() {
        // Create DID with pre-rotation: nextKeyHashes = [hash(B)]
        String hashOfB = PreRotationHashGenerator.generateHash(multikeyB);
        DidWebVhState state = createStateWithPreRotation(
                "example.com", signerA, hashOfB);

        UpdateDidResult result = DidWebVh.deactivate(state, signerA)
                .nextRotationSigner(signerB)
                .execute();

        assertThat(result.getNewEntries()).hasSize(2);

        // Intermediate entry: reveals B, clears nextKeyHashes
        LogEntry intermediate = result.getNewEntries().get(0);
        assertThat(intermediate.getParameters().getUpdateKeys())
                .containsExactly(multikeyB);
        assertThat(intermediate.getParameters().getNextKeyHashes()).isEmpty();

        // Final entry: deactivated=true, updateKeys=[]
        LogEntry finalEntry = result.getNewEntries().get(1);
        assertThat(finalEntry.getParameters().getDeactivated()).isTrue();
        assertThat(finalEntry.getParameters().getUpdateKeys()).isEmpty();

        // Append both and validate
        for (LogEntry e : result.getNewEntries()) {
            state.appendEntry(e);
        }
        ValidationResult vr = DidWebVh.validate(state.getLogEntries(), state.getDid());
        assertThat(vr.isValid()).isTrue();
        assertThat(vr.getActiveParameters().getDeactivated()).isTrue();
    }

    @Test
    void deactivation_withPreRotation_missingNextSigner_throws() {
        String hashOfB = PreRotationHashGenerator.generateHash(multikeyB);
        DidWebVhState state = createStateWithPreRotation(
                "example.com", signerA, hashOfB);

        assertThatThrownBy(() -> DidWebVh.deactivate(state, signerA).execute())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("nextRotationSigner");
    }

    @Test
    void deactivation_alreadyDeactivated_throws() {
        DidWebVhState state = createState("example.com", signerA);
        UpdateDidResult result = DidWebVh.deactivate(state, signerA).execute();
        for (LogEntry e : result.getNewEntries()) {
            state.appendEntry(e);
        }

        assertThatThrownBy(() -> DidWebVh.deactivate(state, signerA).execute())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("deactivated");
    }

    // -------------------------------------------------------------------------
    // End-to-end
    // -------------------------------------------------------------------------

    @Test
    void endToEnd_createUpdateMigrateDeactivate() {
        // Create portable DID
        DidWebVhState state = createPortableState("old.example.com", signerA);

        // Update 3×
        for (int i = 0; i < 3; i++) {
            UpdateDidResult r = DidWebVh.update(state, signerA).execute();
            state.appendEntry(r.getLogEntry());
        }

        // Migrate
        UpdateDidResult migrated = DidWebVh.migrate(state, signerA, "new.example.com")
                .execute();
        state.appendEntry(migrated.getLogEntry());
        // After migration the canonical DID reflects the new domain (SCID preserved).
        assertThat(state.getDid()).contains("new.example.com");

        // Update 2× after migration
        for (int i = 0; i < 2; i++) {
            UpdateDidResult r = DidWebVh.update(state, signerA).execute();
            state.appendEntry(r.getLogEntry());
        }

        // Deactivate
        UpdateDidResult deact = DidWebVh.deactivate(state, signerA).execute();
        for (LogEntry e : deact.getNewEntries()) {
            state.appendEntry(e);
        }

        ValidationResult vr = DidWebVh.validate(state.getLogEntries(), state.getDid());
        assertThat(vr.isValid()).isTrue();
        assertThat(vr.getActiveParameters().getDeactivated()).isTrue();
        // 1 create + 3 updates + 1 migrate + 2 updates + 1 deactivate = 8
        assertThat(state.getLogEntries()).hasSize(8);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static DidWebVhState createState(String domain, Signer signer) {
        CreateDidResult created = DidWebVh.create(domain, signer).execute();
        return DidWebVhState.from(created.getDid(), created.getLogEntry());
    }

    private static DidWebVhState createPortableState(String domain, Signer signer) {
        CreateDidResult created = DidWebVh.create(domain, signer)
                .portable(true)
                .execute();
        return DidWebVhState.from(created.getDid(), created.getLogEntry());
    }

    private static DidWebVhState createStateWithPreRotation(String domain,
                                                            Signer signer,
                                                            String nextKeyHash) {
        CreateDidResult created = DidWebVh.create(domain, signer)
                .nextKeyHashes(Collections.singletonList(nextKeyHash))
                .execute();
        return DidWebVhState.from(created.getDid(), created.getLogEntry());
    }

    private static JsonObject addService(JsonObject doc) {
        JsonArray services = new JsonArray();
        JsonObject svc = new JsonObject();
        svc.addProperty("id", doc.get("id").getAsString() + "#linked-domain");
        svc.addProperty("type", "LinkedDomains");
        svc.addProperty("serviceEndpoint", "https://example.com");
        services.add(svc);
        doc.add("service", services);
        return doc;
    }

    private static Signer makeSigner() {
        AsymmetricCipherKeyPair pair = generateKeyPair();
        Ed25519PrivateKeyParameters priv =
                (Ed25519PrivateKeyParameters) pair.getPrivate();
        Ed25519PublicKeyParameters pub =
                (Ed25519PublicKeyParameters) pair.getPublic();
        String multikey = MultikeyUtil.encode(MultikeyUtil.ED25519_KEY_TYPE,
                pub.getEncoded());
        String vm = "did:key:" + multikey + "#" + multikey;
        return new Signer() {
            @Override
            public String keyType() {
                return "Ed25519";
            }

            @Override
            public String verificationMethod() {
                return vm;
            }

            @Override
            public byte[] sign(byte[] data) {
                Ed25519Signer s = new Ed25519Signer();
                s.init(true, priv);
                s.update(data, 0, data.length);
                return s.generateSignature();
            }
        };
    }

    private static AsymmetricCipherKeyPair generateKeyPair() {
        Ed25519KeyPairGenerator gen = new Ed25519KeyPairGenerator();
        gen.init(new Ed25519KeyGenerationParameters(new SecureRandom()));
        return gen.generateKeyPair();
    }
}
