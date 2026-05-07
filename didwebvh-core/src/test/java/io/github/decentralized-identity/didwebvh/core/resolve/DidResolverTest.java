package io.github.decentralizedidentity.didwebvh.core.resolve;

import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.DidWebVh;
import io.github.decentralizedidentity.didwebvh.core.ResolutionException;
import io.github.decentralizedidentity.didwebvh.core.create.CreateDidResult;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.JsonSupport;
import io.github.decentralizedidentity.didwebvh.core.model.ResolveResult;
import io.github.decentralizedidentity.didwebvh.core.signing.Signer;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessEntry;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessProofCollection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;

import static io.github.decentralizedidentity.didwebvh.core.resolve.ResolveTestSupport.buildUpdateEntry;
import static io.github.decentralizedidentity.didwebvh.core.resolve.ResolveTestSupport.extractMultikey;
import static io.github.decentralizedidentity.didwebvh.core.resolve.ResolveTestSupport.makeTestSigner;
import static io.github.decentralizedidentity.didwebvh.core.resolve.ResolveTestSupport.witnessProofs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DidResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesFromFile() throws IOException {
        Signer signer = makeTestSigner();
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        Path didLog = tempDir.resolve("did.jsonl");
        Files.write(didLog, create.getLogLine().getBytes(StandardCharsets.UTF_8));

        ResolveResult result = new DidResolver().resolveFromFile(didLog);

        assertThat(result.getDidDocument().getId()).isEqualTo(create.getDid());
        assertThat(result.getMetadata().getVersionId())
                .isEqualTo(create.getLogEntry().getVersionId());
    }

    @Test
    void resolveUsesQueryOptionsFromDid() {
        Signer signer = makeTestSigner();
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        LogEntry first = create.getLogEntry();
        JsonObject secondState = first.getState().deepCopy();
        secondState.addProperty("updated", "true");
        LogEntry second = buildUpdateEntry(first, signer, null, secondState);
        String log = first.toJsonLine() + "\n" + second.toJsonLine();

        DidResolver resolver = new DidResolver(new StubRemoteDidFetcher(log),
                new FileDidFetcher(), new LogProcessor());
        ResolveResult result = resolver.resolve(create.getDid() + "?versionNumber=1");

        assertThat(result.getMetadata().getVersionId()).isEqualTo(first.getVersionId());
        assertThat(result.getDidDocument().asJsonObject().has("updated")).isFalse();
    }

    @Test
    void queryMayUseOnlyOneVersionSelector() {
        Signer signer = makeTestSigner();
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        DidResolver resolver = new DidResolver(new StubRemoteDidFetcher(create.getLogLine()),
                new FileDidFetcher(), new LogProcessor());

        assertThatThrownBy(() -> resolver.resolve(create.getDid()
                + "?versionId=" + create.getLogEntry().getVersionId()
                + "&versionNumber=1"))
                .isInstanceOf(ResolutionException.class)
                .extracting("error")
                .isEqualTo("invalidDid");
    }

    @Test
    void whenRequiredWitnessModeDoesNotFetchWitnessForUnwitnessedLog() {
        Signer signer = makeTestSigner();
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        StubRemoteDidFetcher fetcher = new StubRemoteDidFetcher(create.getLogLine());
        DidResolver resolver = new DidResolver(fetcher, new FileDidFetcher(),
                new LogProcessor());

        ResolveResult result = resolver.resolve(create.getDid(), ResolveOptions.builder()
                .witnessFetchMode(ResolveOptions.WitnessFetchMode.WHEN_REQUIRED)
                .build());

        assertThat(result.getDidDocument().getId()).isEqualTo(create.getDid());
        assertThat(fetcher.witnessFetchCount).isZero();
    }

    @Test
    void whenRequiredWitnessModeFetchesWitnessForWitnessedLog() {
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
        StubRemoteDidFetcher fetcher = new StubRemoteDidFetcher(create.getLogLine(),
                JsonSupport.compact().toJson(proofs.getEntries()));
        DidResolver resolver = new DidResolver(fetcher, new FileDidFetcher(),
                new LogProcessor());

        ResolveResult result = resolver.resolve(create.getDid(), ResolveOptions.builder()
                .witnessFetchMode(ResolveOptions.WitnessFetchMode.WHEN_REQUIRED)
                .build());

        assertThat(result.getMetadata().getWitness()).isEqualTo(witnessConfig);
        assertThat(fetcher.witnessFetchCount).isEqualTo(1);
    }

    @Test
    void resolverCanBeConfiguredWithHttpTimeoutAndResponseLimit() {
        DidResolver resolver = new DidResolver(Duration.ofMillis(500), 4096);

        assertThat(resolver).isNotNull();
    }

    private static final class StubRemoteDidFetcher implements RemoteDidFetcher {
        private final String didLog;
        private final String witnessContent;
        private int witnessFetchCount;

        private StubRemoteDidFetcher(String didLog) {
            this(didLog, null);
        }

        private StubRemoteDidFetcher(String didLog, String witnessContent) {
            this.didLog = didLog;
            this.witnessContent = witnessContent;
        }

        @Override
        public String fetchDidLog(String httpsUrl) {
            return didLog;
        }

        @Override
        public String fetchWitnessProofs(String witnessUrl) {
            witnessFetchCount++;
            if (witnessContent != null) {
                return witnessContent;
            }
            throw new ResolutionException("No witness file", "notFound");
        }
    }
}
