package io.tradeops.law;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LawClientTest {
  @Test
  void boundedTransportDoesNotRedirectRetryOrForwardCredentials() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var calls = new AtomicInteger();
    server.createContext(
        "/api/ask",
        exchange -> {
          int count = calls.incrementAndGet();
          assertThat(exchange.getRequestHeaders().getFirst("Cookie")).isNull();
          assertThat(exchange.getRequestHeaders().getFirst("X-CSRF-TOKEN")).isNull();
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          assertThat(body).contains("question").doesNotContain("userId");
          byte[] bytes =
              (count == 3 ? "not json" : "{\"status\":\"OK\"}").getBytes(StandardCharsets.UTF_8);
          if (count == 2) exchange.getResponseHeaders().set("Location", "/api/admin/status");
          exchange.sendResponseHeaders(count == 2 ? 302 : 200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    var client =
        new LawClient("http://127.0.0.1:" + server.getAddress().getPort(), 1, new ObjectMapper());
    try {
      assertThat(client.ask(Map.of("question", "fixture")).path("status").asText()).isEqualTo("OK");
      assertThatThrownBy(() -> client.ask(Map.of("question", "fixture")))
          .hasMessage("LAW_UNAVAILABLE");
      assertThat(calls).hasValue(2);
      assertThatThrownBy(() -> client.ask(Map.of("question", "fixture")))
          .hasMessage("LAW_INVALID_RESPONSE");
      assertThat(calls).hasValue(3);
    } finally {
      server.stop(0);
    }
    assertThatThrownBy(() -> client.ask(Map.of("question", "fixture")))
        .hasMessage("LAW_UNAVAILABLE");
  }

  @Test
  void totalTimeoutAndMissingConfigurationAreSafe() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/ask",
        exchange -> {
          try {
            Thread.sleep(1500);
            exchange.close();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
    server.start();
    try {
      var client =
          new LawClient("http://127.0.0.1:" + server.getAddress().getPort(), 1, new ObjectMapper());
      assertThatThrownBy(() -> client.ask(Map.of("question", "fixture"))).hasMessage("LAW_TIMEOUT");
    } finally {
      server.stop(0);
    }
    assertThatThrownBy(() -> new LawClient("", 1, new ObjectMapper()).ask(Map.of()))
        .hasMessage("LAW_UNAVAILABLE");
  }
}
