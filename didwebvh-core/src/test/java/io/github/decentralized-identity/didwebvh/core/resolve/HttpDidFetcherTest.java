package io.github.decentralizedidentity.didwebvh.core.resolve;

import io.github.decentralizedidentity.didwebvh.core.ResolutionException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpDidFetcherTest {

    @Test
    void fetchDidLogReturnsBody() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("line1\nline2"));
            server.start();

            String body = new HttpDidFetcher().fetchDidLog(
                    server.url("/.well-known/did.jsonl").toString());

            assertThat(body).isEqualTo("line1\nline2");
        }
    }

    @Test
    void notFoundThrowsResolutionException() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(404));
            server.start();

            assertThatThrownBy(() -> new HttpDidFetcher().fetchDidLog(
                    server.url("/.well-known/did.jsonl").toString()))
                    .isInstanceOf(ResolutionException.class)
                    .extracting("error")
                    .isEqualTo("notFound");
        }
    }

    @Test
    void responseLargerThanLimitThrows() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("123456"));
            server.start();

            assertThatThrownBy(() -> new HttpDidFetcher(Duration.ofSeconds(1), 5)
                    .fetchDidLog(server.url("/.well-known/did.jsonl").toString()))
                    .isInstanceOf(ResolutionException.class)
                    .hasMessageContaining("exceeds");
        }
    }

    @Test
    void timeoutThrowsResolutionException() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setBody("slow")
                    .setBodyDelay(250, TimeUnit.MILLISECONDS));
            server.start();

            assertThatThrownBy(() -> new HttpDidFetcher(Duration.ofMillis(50), 1024)
                    .fetchDidLog(server.url("/.well-known/did.jsonl").toString()))
                    .isInstanceOf(ResolutionException.class)
                    .extracting("error")
                    .isEqualTo("httpError");
        }
    }
}
