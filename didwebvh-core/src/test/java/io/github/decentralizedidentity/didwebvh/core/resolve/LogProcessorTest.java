package io.github.decentralizedidentity.didwebvh.core.resolve;

import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.DidWebVh;
import io.github.decentralizedidentity.didwebvh.core.ResolutionException;
import io.github.decentralizedidentity.didwebvh.core.create.CreateDidResult;
import io.github.decentralizedidentity.didwebvh.core.model.JsonSupport;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.model.ResolveResult;
import io.github.decentralizedidentity.didwebvh.core.signing.Signer;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessEntry;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessProofCollection;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;

import static io.github.decentralizedidentity.didwebvh.core.resolve.ResolveTestSupport.buildUpdateEntry;
import static io.github.decentralizedidentity.didwebvh.core.resolve.ResolveTestSupport.extractMultikey;
import static io.github.decentralizedidentity.didwebvh.core.resolve.ResolveTestSupport.makeTestSigner;
import static io.github.decentralizedidentity.didwebvh.core.resolve.ResolveTestSupport.witnessProofs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogProcessorTest {

    private final LogProcessor processor = new LogProcessor();

    @Test
    void resolvesLatestEntryWithMetadata() {
        Signer signer = makeTestSigner();
        CreateDidResult create = DidWebVh.create("example.com", signer)
                .ttl(600)
                .execute();

        ResolveResult result = processor.process(create.getLogLine(), null,
                create.getDid(), ResolveOptions.defaults());

        assertThat(result.getDidDocument().getId()).isEqualTo(create.getDid());
        assertThat(result.getMetadata().getVersionId())
                .isEqualTo(create.getLogEntry().getVersionId());
        assertThat(result.getMetadata().getCreated())
                .isEqualTo(create.getLogEntry().getVersionTime());
        assertThat(result.getMetadata().getTtl()).isEqualTo("600");
        assertThat(result.getMetadata().getDeactivated()).isFalse();
    }

    @Test
    void resolvesSpecificVersions() {
        Signer signer = makeTestSigner();
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        LogEntry first = create.getLogEntry();

        JsonObject secondState = first.getState().deepCopy();
        secondState.addProperty("updated", "true");
        LogEntry second = buildUpdateEntry(first, signer, null, secondState);
        String log = first.toJsonLine() + "\n" + second.toJsonLine();
        String justBeforeSecond = Instant.parse(second.getVersionTime())
                .minusNanos(1)
                .toString();

        ResolveResult latest = processor.process(log, null, create.getDid(),
                ResolveOptions.defaults());
        ResolveResult byVersionId = processor.process(log, null, create.getDid(),
                ResolveOptions.builder().versionId(first.getVersionId()).build());
        ResolveResult byVersionNumber = processor.process(log, null, create.getDid(),
                ResolveOptions.builder().versionNumber(1).build());
        ResolveResult byVersionTime = processor.process(log, null, create.getDid(),
                ResolveOptions.builder().versionTime(justBeforeSecond).build());

        assertThat(latest.getDidDocument().asJsonObject().get("updated").getAsString())
                .isEqualTo("true");
        assertThat(byVersionId.getMetadata().getVersionId()).isEqualTo(first.getVersionId());
        assertThat(byVersionNumber.getMetadata().getVersionId()).isEqualTo(first.getVersionId());
        assertThat(byVersionTime.getMetadata().getVersionId()).isEqualTo(first.getVersionId());
    }

    @Test
    void versionTimeSelectsLastEntryActiveAtRequestedTime() {
        Signer signer = makeTestSigner();
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        LogEntry first = create.getLogEntry();

        JsonObject secondState = first.getState().deepCopy();
        secondState.addProperty("active", "second");
        LogEntry second = buildUpdateEntry(first, signer, null, secondState);
        JsonObject thirdState = secondState.deepCopy();
        thirdState.addProperty("active", "third");
        LogEntry third = buildUpdateEntry(second, signer, null, thirdState);
        String log = first.toJsonLine() + "\n" + second.toJsonLine()
                + "\n" + third.toJsonLine();
        String beforeThird = Instant.parse(third.getVersionTime()).minusNanos(1).toString();

        ResolveResult result = processor.process(log, null, create.getDid(),
                ResolveOptions.builder().versionTime(beforeThird).build());

        assertThat(result.getMetadata().getVersionId()).isEqualTo(second.getVersionId());
        assertThat(result.getDidDocument().asJsonObject().get("active").getAsString())
                .isEqualTo("second");
    }

    @Test
    void multipleVersionSelectorsThrowInvalidDid() {
        Signer signer = makeTestSigner();
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();

        assertThatThrownBy(() -> processor.process(create.getLogLine(), null,
                create.getDid(), ResolveOptions.builder()
                        .versionId(create.getLogEntry().getVersionId())
                        .versionNumber(1)
                        .build()))
                .isInstanceOf(ResolutionException.class)
                .extracting("error")
                .isEqualTo("invalidDid");
    }

    @Test
    void deactivatedDidReturnsMetadataWithoutDocument() {
        Signer signer = makeTestSigner();
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        LogEntry deactivated = buildUpdateEntry(create.getLogEntry(), signer,
                new Parameters().setDeactivated(true), null);
        String log = create.getLogLine() + "\n" + deactivated.toJsonLine();

        ResolveResult result = processor.process(log, null, create.getDid(),
                ResolveOptions.defaults());

        assertThat(result.getDidDocument()).isNull();
        assertThat(result.getMetadata().getDeactivated()).isTrue();
        assertThat(result.getMetadata().getVersionId())
                .isEqualTo(deactivated.getVersionId());
    }

    @Test
    void tamperedLogThrowsInvalidDid() {
        Signer signer = makeTestSigner();
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        LogEntry tampered = new LogEntry()
                .setVersionId(create.getLogEntry().getVersionId())
                .setVersionTime(create.getLogEntry().getVersionTime())
                .setParameters(create.getLogEntry().getParameters())
                .setState(create.getLogEntry().getState().deepCopy())
                .setProof(create.getLogEntry().getProof());
        tampered.getState().addProperty("tampered", "true");

        assertThatThrownBy(() -> processor.process(tampered.toJsonLine(), null,
                create.getDid(), ResolveOptions.defaults()))
                .isInstanceOf(ResolutionException.class)
                .extracting("error")
                .isEqualTo("invalidDid");
    }

    @Test
    void validatesWitnessProofsWhenConfigured() {
        Signer author = makeTestSigner();
        Signer witness = makeTestSigner();
        String witnessDid = "did:key:" + extractMultikey(witness.verificationMethod());
        WitnessConfig witnessConfig = new WitnessConfig(1,
                Collections.singletonList(new WitnessEntry(witnessDid)));
        CreateDidResult create = DidWebVh.create("example.com", author)
                .witness(witnessConfig)
                .execute();
        WitnessProofCollection proofs = witnessProofs(
                create.getLogEntry().getVersionId(), witness);

        ResolveResult result = processor.process(create.getLogLine(),
                JsonSupport.compact().toJson(proofs.getEntries()), create.getDid(),
                ResolveOptions.defaults());

        assertThat(result.getDidDocument().getId()).isEqualTo(create.getDid());
        assertThat(result.getMetadata().getWitness()).isEqualTo(witnessConfig);
    }

    @Test
    void missingWitnessProofsThrowInvalidDid() {
        Signer author = makeTestSigner();
        Signer witness = makeTestSigner();
        String witnessDid = "did:key:" + extractMultikey(witness.verificationMethod());
        CreateDidResult create = DidWebVh.create("example.com", author)
                .witness(new WitnessConfig(1,
                        Collections.singletonList(new WitnessEntry(witnessDid))))
                .execute();

        assertThatThrownBy(() -> processor.process(create.getLogLine(), null,
                create.getDid(), ResolveOptions.defaults()))
                .isInstanceOf(ResolutionException.class)
                .hasMessageContaining("Witness proofs");
    }

    @Test
    void problemDetailsUseDidwebvhProblemType() {
        ResolutionException exception = new ResolutionException(
                "Bad log", "invalidDid");

        assertThat(exception.getProblemDetails().get("type").getAsString())
                .isEqualTo("urn:didwebvh:error:invalidDid");
        assertThat(exception.getProblemDetails().get("title").getAsString())
                .isEqualTo("Invalid DID");
    }

}
