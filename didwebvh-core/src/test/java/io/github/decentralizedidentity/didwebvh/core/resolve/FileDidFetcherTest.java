package io.github.decentralizedidentity.didwebvh.core.resolve;

import io.github.decentralizedidentity.didwebvh.core.ResolutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileDidFetcherTest {

    @Test
    void fetchDidLogReadsFile(@TempDir Path tmp) throws Exception {
        Path log = tmp.resolve("did.jsonl");
        Files.write(log, "hello".getBytes(StandardCharsets.UTF_8));
        FileDidFetcher fetcher = new FileDidFetcher();
        assertThat(fetcher.fetchDidLog(log)).isEqualTo("hello");
    }

    @Test
    void fetchWitnessProofsReadsFile(@TempDir Path tmp) throws Exception {
        Path proofs = tmp.resolve("witness.json");
        Files.write(proofs, "{}".getBytes(StandardCharsets.UTF_8));
        assertThat(new FileDidFetcher().fetchWitnessProofs(proofs)).isEqualTo("{}");
    }

    @Test
    void fetchDidLogRejectsNullPath() {
        assertThatThrownBy(() -> new FileDidFetcher().fetchDidLog(null))
                .isInstanceOf(ResolutionException.class)
                .hasMessageContaining("did log path is required")
                .extracting("error").isEqualTo("invalidDid");
    }

    @Test
    void fetchWitnessProofsRejectsNullPath() {
        assertThatThrownBy(() -> new FileDidFetcher().fetchWitnessProofs(null))
                .isInstanceOf(ResolutionException.class)
                .hasMessageContaining("witness proofs path is required")
                .extracting("error").isEqualTo("invalidDid");
    }

    @Test
    void fetchDidLogMapsMissingFileToNotFound(@TempDir Path tmp) {
        Path missing = tmp.resolve("nope.jsonl");
        assertThatThrownBy(() -> new FileDidFetcher().fetchDidLog(missing))
                .isInstanceOf(ResolutionException.class)
                .hasMessageContaining("Unable to read did log")
                .extracting("error").isEqualTo("notFound");
    }

    @Test
    void fetchWitnessProofsMapsMissingFileToNotFound(@TempDir Path tmp) {
        Path missing = tmp.resolve("nope.json");
        assertThatThrownBy(() -> new FileDidFetcher().fetchWitnessProofs(missing))
                .isInstanceOf(ResolutionException.class)
                .hasMessageContaining("Unable to read witness proofs")
                .extracting("error").isEqualTo("notFound");
    }
}
