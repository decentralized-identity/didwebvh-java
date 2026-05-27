package io.github.decentralizedidentity.didwebvh.core;

import io.github.decentralizedidentity.didwebvh.core.create.CreateDidResult;
import io.github.decentralizedidentity.didwebvh.core.crypto.MultikeyUtil;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.signing.Signer;
import io.github.decentralizedidentity.didwebvh.core.update.UpdateDidResult;
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

import static org.assertj.core.api.Assertions.assertThat;

class DidWebVhStateTest {

    private static Signer signer;

    @BeforeAll
    static void setUp() {
        signer = makeSigner();
    }

    @Test
    void from_singleEntry_exposesDidAndLastEntry() {
        CreateDidResult created = DidWebVh.create("example.com", signer).execute();
        DidWebVhState state = DidWebVhState.from(created.getDid(), created.getLogEntry());

        assertThat(state.getDid()).isEqualTo(created.getDid());
        assertThat(state.getLogEntries()).hasSize(1);
        assertThat(state.getLastEntry()).isEqualTo(created.getLogEntry());
        assertThat(state.isValidated()).isFalse();
        assertThat(state.getActiveParameters()).isNull();
    }

    @Test
    void getLogEntries_isUnmodifiable() {
        DidWebVhState state = createState();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> state.getLogEntries().add(state.getLastEntry()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validate_setsActiveParametersAndMarksValidated() {
        DidWebVhState state = createState();

        ValidationResult result = state.validate();

        assertThat(result.isValid()).isTrue();
        assertThat(state.isValidated()).isTrue();
        assertThat(state.getActiveParameters()).isNotNull();
    }

    @Test
    void appendEntry_clearsValidation() {
        DidWebVhState state = createState();
        state.validate();
        assertThat(state.isValidated()).isTrue();

        UpdateDidResult updated = DidWebVh.update(state, signer).execute();
        state.appendEntry(updated.getLogEntry());

        assertThat(state.isValidated()).isFalse();
        assertThat(state.getActiveParameters()).isNull();
        assertThat(state.getLogEntries()).hasSize(2);
    }

    @Test
    void appendEntry_updatesDidWhenDocumentIdChanges() {
        // Simulate migration: append an entry whose state.id is a new DID
        DidWebVhState state = createPortableState();
        String oldDid = state.getDid();

        UpdateDidResult migrated = DidWebVh.migrate(state, signer, "new.example.com")
                .execute();
        state.appendEntry(migrated.getLogEntry());

        assertThat(state.getDid()).isNotEqualTo(oldDid);
        assertThat(state.getDid()).contains("new.example.com");
        // SCID is preserved
        String oldScid = oldDid.split(":")[2];
        assertThat(state.getDid()).contains(oldScid);
    }

    @Test
    void isDeactivated_falseForFreshState() {
        DidWebVhState state = createState();
        assertThat(state.isDeactivated()).isFalse();
    }

    @Test
    void isDeactivated_trueAfterDeactivation() {
        DidWebVhState state = createState();
        UpdateDidResult deact = DidWebVh.deactivate(state, signer).execute();
        for (LogEntry e : deact.getNewEntries()) {
            state.appendEntry(e);
        }
        assertThat(state.isDeactivated()).isTrue();
    }

    @Test
    void accumulateParameters_mergesAcrossEntries() {
        DidWebVhState state = createState();
        Parameters change = new Parameters().setTtl(9999);
        UpdateDidResult r = DidWebVh.update(state, signer)
                .changedParameters(change)
                .execute();
        state.appendEntry(r.getLogEntry());

        Parameters active = state.accumulateParameters();
        assertThat(active.getTtl()).isEqualTo(9999);
        assertThat(active.getMethod()).isEqualTo("did:webvh:1.0");
    }

    @Test
    void toDidLog_fromDidLog_roundTrip() {
        DidWebVhState state = createState();
        UpdateDidResult r = DidWebVh.update(state, signer).execute();
        state.appendEntry(r.getLogEntry());

        String jsonl = state.toDidLog();
        DidWebVhState reloaded = DidWebVhState.fromDidLog(state.getDid(), jsonl);

        assertThat(reloaded.getLogEntries()).hasSize(2);
        assertThat(reloaded.toDidLog()).isEqualTo(jsonl);
    }

    @Test
    void toJson_fromJson_roundTrip() {
        DidWebVhState state = createState();
        UpdateDidResult r = DidWebVh.update(state, signer).execute();
        state.appendEntry(r.getLogEntry());

        String json = state.toJson();
        DidWebVhState reloaded = DidWebVhState.fromJson(json);

        assertThat(reloaded.getDid()).isEqualTo(state.getDid());
        assertThat(reloaded.getLogEntries()).hasSize(2);
        assertThat(reloaded.toDidLog()).isEqualTo(state.toDidLog());
    }

    @Test
    void fromDidLog_ignoresBlankLines() {
        DidWebVhState state = createState();
        String jsonl = state.toDidLog() + "\n\n";
        DidWebVhState reloaded = DidWebVhState.fromDidLog(state.getDid(), jsonl);
        assertThat(reloaded.getLogEntries()).hasSize(1);
    }

    // -------------------------------------------------------------------------

    private static DidWebVhState createState() {
        CreateDidResult created = DidWebVh.create("example.com", signer).execute();
        return DidWebVhState.from(created.getDid(), created.getLogEntry());
    }

    private static DidWebVhState createPortableState() {
        CreateDidResult created = DidWebVh.create("old.example.com", signer)
                .portable(true)
                .execute();
        return DidWebVhState.from(created.getDid(), created.getLogEntry());
    }

    private static Signer makeSigner() {
        AsymmetricCipherKeyPair pair = generateKeyPair();
        Ed25519PrivateKeyParameters priv = (Ed25519PrivateKeyParameters) pair.getPrivate();
        Ed25519PublicKeyParameters pub = (Ed25519PublicKeyParameters) pair.getPublic();
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
