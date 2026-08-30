package io.github.ssforu.pin4u.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Tag("integration")
class WebClientTimeoutTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private WebClient buildClient(Duration connectTimeout, Duration responseTimeout) {
        HttpClient httpClient = WebClientConfig.buildHttpClient(connectTimeout, responseTimeout);
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(server.url("/").toString())
                .build();
    }

    @Test
    @DisplayName("kakaoSearch: 3초 타임아웃 — 5초 지연 응답 시 타임아웃 예외 발생")
    void kakaoSearch_responseTimeout() {
        server.enqueue(new MockResponse()
                .setBody("{}")
                .setHeadersDelay(5, TimeUnit.SECONDS));

        WebClient client = buildClient(Duration.ofSeconds(2), Duration.ofSeconds(3));

        long start = System.nanoTime();
        assertThatThrownBy(() ->
                client.get().uri("/test").retrieve().bodyToMono(String.class).block())
                .isInstanceOf(Exception.class);
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(elapsed).as("3s 타임아웃이므로 5s 전에 끊어야 한다").isLessThan(4500);
    }

    @Test
    @DisplayName("openai: 20초 타임아웃 — 정상 응답 성공")
    void openai_normalResponse_succeeds() {
        server.enqueue(new MockResponse()
                .setBody("{\"ok\":true}")
                .addHeader("Content-Type", "application/json"));

        WebClient client = buildClient(Duration.ofSeconds(2), Duration.ofSeconds(20));

        String result = client.get().uri("/test").retrieve().bodyToMono(String.class).block();
        assertThat(result).contains("ok");
    }

    @Test
    @DisplayName("kakaoOAuth: 3초 타임아웃 — 5초 지연 응답 시 타임아웃 예외 발생")
    void kakaoOAuth_responseTimeout() {
        server.enqueue(new MockResponse()
                .setBody("{}")
                .setHeadersDelay(5, TimeUnit.SECONDS));

        WebClient client = buildClient(Duration.ofSeconds(2), Duration.ofSeconds(3));

        long start = System.nanoTime();
        assertThatThrownBy(() ->
                client.get().uri("/test").retrieve().bodyToMono(String.class).block())
                .isInstanceOf(Exception.class);
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(elapsed).isLessThan(4500);
    }
}
