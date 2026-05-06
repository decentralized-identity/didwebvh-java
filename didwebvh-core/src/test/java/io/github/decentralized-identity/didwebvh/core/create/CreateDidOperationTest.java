package io.github.decentralizedidentity.didwebvh.core.create;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.DidWebVh;
import io.github.decentralizedidentity.didwebvh.core.ValidationException;
import io.github.decentralizedidentity.didwebvh.core.crypto.EntryHashGenerator;
import io.github.decentralizedidentity.didwebvh.core.crypto.MultikeyUtil;
import io.github.decentralizedidentity.didwebvh.core.crypto.PreRotationHashGenerator;
import io.github.decentralizedidentity.didwebvh.core.crypto.ScidGenerator;
import io.github.decentralizedidentity.didwebvh.core.model.DataIntegrityProof;
import io.github.decentralizedidentity.didwebvh.core.model.DidDocument;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.signing.ProofVerifier;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateDidOperationTest {

    private static Signer testSigner;
    private static String testMultikey;

    @BeforeAll
    static void setUp() {
        Ed25519KeyPairGenerator gen = new Ed25519KeyPairGenerator();
        gen.init(new Ed25519KeyGenerationParameters(new SecureRandom()));
        AsymmetricCipherKeyPair pair = gen.generateKeyPair();
        Ed25519PrivateKeyParameters priv =
                (Ed25519PrivateKeyParameters) pair.getPrivate();
        Ed25519PublicKeyParameters pub =
                (Ed25519PublicKeyParameters) pair.getPublic();

        testMultikey = MultikeyUtil.encode(
                MultikeyUtil.ED25519_KEY_TYPE, pub.getEncoded());
        String vm = "did:key:" + testMultikey + "#" + testMultikey;

        testSigner = new Signer() {
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

    @Test
    void minimalCreation() {
        CreateDidResult result = DidWebVh.create("example.com", testSigner)
                .execute();

        // DID format
        assertThat(result.getDid()).startsWith("did:webvh:");
        assertThat(result.getDid()).endsWith(":example.com");
        assertThat(result.getDid()).doesNotContain("{SCID}");

        // Log entry structure
        LogEntry entry = result.getLogEntry();
        assertThat(entry.getVersionId()).startsWith("1-");
        assertThat(entry.getVersionTime()).isNotNull();

        // Parameters
        Parameters params = entry.getParameters();
        assertThat(params.getMethod()).isEqualTo("did:webvh:1.0");
        assertThat(params.getScid()).isNotNull();
        assertThat(params.getScid()).doesNotContain("{SCID}");
        assertThat(params.getUpdateKeys()).containsExactly(testMultikey);

        // State (DID Document)
        DidDocument doc = new DidDocument(entry.getState());
        assertThat(doc.getId()).isEqualTo(result.getDid());
        assertThat(doc.getController()).containsExactly(result.getDid());

        // Proof
        assertThat(entry.getProof()).hasSize(1);
        DataIntegrityProof proof = entry.getProof().get(0);
        assertThat(proof.getType()).isEqualTo("DataIntegrityProof");
        assertThat(proof.getCryptosuite()).isEqualTo("eddsa-jcs-2022");
    }

    @Test
    void scidIsValid() {
        CreateDidResult result = DidWebVh.create("example.com", testSigner)
                .execute();

        LogEntry entry = result.getLogEntry();
        String scid = entry.getParameters().getScid();

        assertThat(ScidGenerator.verify(scid, entry)).isTrue();
    }

    @Test
    void entryHashIsValid() {
        CreateDidResult result = DidWebVh.create("example.com", testSigner)
                .execute();

        LogEntry entry = result.getLogEntry();
        String scid = entry.getParameters().getScid();

        assertThat(EntryHashGenerator.verify(entry, scid)).isTrue();
    }

    @Test
    void proofIsValid() {
        CreateDidResult result = DidWebVh.create("example.com", testSigner)
                .execute();

        LogEntry entry = result.getLogEntry();
        DataIntegrityProof proof = entry.getProof().get(0);

        assertThat(ProofVerifier.verify(proof, entry)).isTrue();
    }

    @Test
    void proofIsAuthorized() {
        CreateDidResult result = DidWebVh.create("example.com", testSigner)
                .execute();

        LogEntry entry = result.getLogEntry();
        DataIntegrityProof proof = entry.getProof().get(0);

        assertThat(ProofVerifier.isAuthorized(proof,
                entry.getParameters().getUpdateKeys())).isTrue();
    }

    @Test
    void logLineIsCompactJson() {
        CreateDidResult result = DidWebVh.create("example.com", testSigner)
                .execute();

        String logLine = result.getLogLine();
        assertThat(logLine).doesNotContain("\n");
        assertThat(logLine).doesNotContain("  "); // no indentation
    }

    @Test
    void jsonlRoundTrip() {
        CreateDidResult result = DidWebVh.create("example.com", testSigner)
                .execute();

        LogEntry parsed = LogEntry.fromJsonLine(result.getLogLine());
        assertThat(parsed.getVersionId())
                .isEqualTo(result.getLogEntry().getVersionId());
        assertThat(parsed.getVersionTime())
                .isEqualTo(result.getLogEntry().getVersionTime());
        assertThat(parsed.getParameters().getScid())
                .isEqualTo(result.getLogEntry().getParameters().getScid());
    }

    @Test
    void createWithPath() {
        CreateDidResult result = DidWebVh.create("example.com", testSigner)
                .path("dids:issuer")
                .execute();

        assertThat(result.getDid()).endsWith(":example.com:dids:issuer");
        DidDocument doc = new DidDocument(result.getLogEntry().getState());
        assertThat(doc.getId()).isEqualTo(result.getDid());
    }

    @Test
    void createWithPort() {
        CreateDidResult result = DidWebVh.create("example.com%3A3000", testSigner)
                .path("dids:issuer")
                .execute();

        assertThat(result.getDid())
                .contains("example.com%3A3000:dids:issuer");
    }

    @Test
    void createWithPortable() {
        CreateDidResult result = DidWebVh.create("example.com", testSigner)
                .portable(true)
                .execute();

        assertThat(result.getLogEntry().getParameters().getPortable())
                .isTrue();
    }

    @Test
    void createWithTtl() {
        CreateDidResult result = DidWebVh.create("example.com", testSigner)
                .ttl(3600)
                .execute();

        assertThat(result.getLogEntry().getParameters().getTtl())
                .isEqualTo(3600);
    }

    @Test
    void createWithAlsoKnownAs() {
        CreateDidResult result = DidWebVh.create("example.com", testSigner)
                .alsoKnownAs(Arrays.asList(
                        "https://example.com/user",
                        "mailto:user@example.com"))
                .execute();

        DidDocument doc = new DidDocument(result.getLogEntry().getState());
        assertThat(doc.getAlsoKnownAs()).containsExactly(
                "https://example.com/user",
                "mailto:user@example.com");
    }

    @Test
    void createWithWitness() {
        WitnessConfig witness = new WitnessConfig(1,
                Collections.singletonList(
                        new WitnessEntry("did:key:z6MkWitness")));

        CreateDidResult result = DidWebVh.create("example.com", testSigner)
                .witness(witness)
                .execute();

        WitnessConfig resultWitness =
                result.getLogEntry().getParameters().getWitness();
        assertThat(resultWitness.getThreshold()).isEqualTo(1);
        assertThat(resultWitness.getWitnesses()).hasSize(1);
    }

    @Test
    void createWithWatchers() {
        CreateDidResult result = DidWebVh.create("example.com", testSigner)
                .watchers(Collections.singletonList(
                        "https://watcher.example.com"))
                .execute();

        assertThat(result.getLogEntry().getParameters().getWatchers())
                .containsExactly("https://watcher.example.com");
    }

    @Test
    void createWithPreRotation() {
        String nextKeyHash = PreRotationHashGenerator.generateHash(testMultikey);

        CreateDidResult result = DidWebVh.create("example.com", testSigner)
                .nextKeyHashes(Collections.singletonList(nextKeyHash))
                .execute();

        assertThat(result.getLogEntry().getParameters().getNextKeyHashes())
                .containsExactly(nextKeyHash);
    }

    @Test
    void createWithAdditionalContent() {
        JsonObject additional = new JsonObject();
        JsonArray service = new JsonArray();
        JsonObject svc = new JsonObject();
        svc.addProperty("id", "#linked-domain");
        svc.addProperty("type", "LinkedDomains");
        svc.addProperty("serviceEndpoint", "https://example.com");
        service.add(svc);
        additional.add("service", service);

        CreateDidResult result = DidWebVh.create("example.com", testSigner)
                .additionalDocumentContent(additional)
                .execute();

        DidDocument doc = new DidDocument(result.getLogEntry().getState());
        assertThat(doc.getService()).isNotNull();
        assertThat(doc.getService().size()).isEqualTo(1);
    }

    @Test
    void createWithAllOptions() {
        String nextKeyHash = PreRotationHashGenerator.generateHash(testMultikey);
        WitnessConfig witness = new WitnessConfig(1,
                Collections.singletonList(
                        new WitnessEntry("did:key:z6MkWitness")));

        CreateDidResult result = DidWebVh.create("example.com", testSigner)
                .path("dids:issuer")
                .portable(true)
                .ttl(3600)
                .alsoKnownAs(Collections.singletonList("https://example.com/user"))
                .witness(witness)
                .watchers(Collections.singletonList("https://watcher.example.com"))
                .nextKeyHashes(Collections.singletonList(nextKeyHash))
                .execute();

        LogEntry entry = result.getLogEntry();
        Parameters params = entry.getParameters();

        assertThat(result.getDid()).contains(":example.com:dids:issuer");
        assertThat(params.getPortable()).isTrue();
        assertThat(params.getTtl()).isEqualTo(3600);
        assertThat(params.getWitness().getThreshold()).isEqualTo(1);
        assertThat(params.getWatchers()).hasSize(1);
        assertThat(params.getNextKeyHashes()).hasSize(1);

        // Still valid
        assertThat(ScidGenerator.verify(params.getScid(), entry)).isTrue();
        assertThat(EntryHashGenerator.verify(entry, params.getScid())).isTrue();
        assertThat(ProofVerifier.verify(entry.getProof().get(0), entry)).isTrue();
    }

    @Test
    void versionTimeIsValidIso8601() {
        CreateDidResult result = DidWebVh.create("example.com", testSigner)
                .execute();

        String versionTime = result.getLogEntry().getVersionTime();
        // Should parse without error
        Instant parsed = Instant.parse(versionTime);
        assertThat(parsed).isBefore(Instant.now().plusSeconds(5));
    }

    @Test
    void noScidPlaceholderInOutput() {
        CreateDidResult result = DidWebVh.create("example.com", testSigner)
                .execute();

        String logLine = result.getLogLine();
        assertThat(logLine).doesNotContain("{SCID}");
        assertThat(result.getDid()).doesNotContain("{SCID}");
    }

    @Test
    void nullDomainThrows() {
        assertThatThrownBy(() ->
                DidWebVh.create(null, testSigner).execute())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("domain");
    }

    @Test
    void emptyDomainThrows() {
        assertThatThrownBy(() ->
                DidWebVh.create("", testSigner).execute())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("domain");
    }

    @Test
    void nullSignerThrows() {
        assertThatThrownBy(() ->
                DidWebVh.create("example.com", null).execute())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("signer");
    }

    @Test
    void negativeTtlThrows() {
        assertThatThrownBy(() ->
                DidWebVh.create("example.com", testSigner)
                        .ttl(-1).execute())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ttl must be non-negative");
    }

    @Test
    void zeroTtlMeansDoNotCache() {
        // ttl=0 is valid per spec: "do not cache"
        CreateDidResult result = DidWebVh.create("example.com", testSigner)
                .ttl(0).execute();
        assertThat(result.getLogEntry().getParameters().getTtl()).isEqualTo(0);
    }

    @Test
    void nextKeyHashEmptyEntryThrows() {
        assertThatThrownBy(() ->
                DidWebVh.create("example.com", testSigner)
                        .nextKeyHashes(Collections.singletonList(""))
                        .execute())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("non-empty");
    }

    @Test
    void didDocumentHasVerificationMethod() {
        CreateDidResult result = DidWebVh.create("example.com", testSigner)
                .execute();

        DidDocument doc = new DidDocument(result.getLogEntry().getState());
        JsonArray vms = doc.getVerificationMethod();
        assertThat(vms).isNotNull();
        assertThat(vms.size()).isEqualTo(1);

        JsonObject vm = vms.get(0).getAsJsonObject();
        assertThat(vm.get("type").getAsString()).isEqualTo("Multikey");
        assertThat(vm.get("publicKeyMultibase").getAsString())
                .isEqualTo(testMultikey);
        assertThat(vm.get("controller").getAsString())
                .isEqualTo(result.getDid());
    }
}
