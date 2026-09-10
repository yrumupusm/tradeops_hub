package io.tradeops.bis;

import io.tradeops.error.OperationException;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BisHttpClient {
  public record Download(int status, String url, byte[] bytes, String etag, String lastModified) {}

  private final Set<String> hosts;
  private final HttpClient client;

  public BisHttpClient(
      @Value("${tradeops.bis.allowed-hosts:bis.gov,www.bis.gov,media.bis.gov}") String hosts) {
    this.hosts = new HashSet<>(Arrays.asList(hosts.toLowerCase(Locale.ROOT).split(",")));
    client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  public URI allowed(String value) {
    try {
      URI uri = URI.create(value);
      if (!"https".equalsIgnoreCase(uri.getScheme())
          || uri.getHost() == null
          || !hosts.contains(uri.getHost().toLowerCase(Locale.ROOT))
          || uri.getUserInfo() != null
          || (uri.getPort() != -1 && uri.getPort() != 443)) throw new IllegalArgumentException();
      for (InetAddress address : InetAddress.getAllByName(uri.getHost()))
        if (address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()) throw new IllegalArgumentException();
      return uri;
    } catch (Exception e) {
      throw new OperationException("SOURCE_URL_NOT_ALLOWED", 422);
    }
  }

  public Download get(String url, String etag, String modified) {
    URI target = allowed(url);
    int attempts = 0, redirects = 0;
    while (true)
      try {
        var builder =
            HttpRequest.newBuilder(target)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "TradeOpsHub/1.0 (BIS public list retrieval)")
                .header("Accept", "text/html,text/csv,text/plain,*/*");
        if (etag != null && !etag.isBlank()) builder.header("If-None-Match", etag);
        if (modified != null && !modified.isBlank()) builder.header("If-Modified-Since", modified);
        var response =
            client.send(builder.GET().build(), info -> new LimitedBody(32 * 1024 * 1024));
        int status = response.statusCode();
        if (Set.of(301, 302, 303, 307, 308).contains(status)) {
          if (++redirects > 4) throw new OperationException("REDIRECT_LIMIT", 422);
          target =
              allowed(
                  target
                      .resolve(response.headers().firstValue("location").orElseThrow())
                      .toString());
          etag = null;
          modified = null;
          continue;
        }
        if (status == 429 || status >= 500) {
          if (++attempts >= 3) throw new OperationException("SOURCE_TEMPORARILY_UNAVAILABLE", 503);
          long seconds = Math.min(30L, attempts * 2L);
          String retry = response.headers().firstValue("Retry-After").orElse("");
          if (!retry.isBlank())
            try {
              seconds = Long.parseLong(retry);
            } catch (NumberFormatException e) {
              try {
                seconds =
                    Math.max(
                        1,
                        Duration.between(
                                java.time.Instant.now(),
                                java.time.ZonedDateTime.parse(
                                        retry,
                                        java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                                    .toInstant())
                            .toSeconds());
              } catch (Exception ignored) {
              }
            }
          if (seconds > 30) throw new OperationException("SOURCE_RETRY_LATER", 503);
          Thread.sleep(Math.max(1, seconds) * 1000);
          continue;
        }
        if (status != 200 && status != 304)
          throw new OperationException("SOURCE_HTTP_" + status, 422);
        if (response.body().length > 32 * 1024 * 1024)
          throw new OperationException("SOURCE_TOO_LARGE", 422);
        return new Download(
            status,
            target.toString(),
            response.body(),
            response.headers().firstValue("ETag").orElse(null),
            response.headers().firstValue("Last-Modified").orElse(null));
      } catch (OperationException e) {
        throw e;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new OperationException("COLLECTION_INTERRUPTED", 503);
      } catch (Exception e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause())
          if (cause instanceof OperationException safe) throw safe;
        if (++attempts >= 3) throw new OperationException("SOURCE_CONNECTION_FAILED", 503);
      }
  }

  static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final int limit;
    private int received;
    private java.util.concurrent.Flow.Subscription subscription;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final java.util.concurrent.CompletableFuture<byte[]> result =
        new java.util.concurrent.CompletableFuture<>();

    LimitedBody(int limit) {
      this.limit = limit;
    }

    public java.util.concurrent.CompletionStage<byte[]> getBody() {
      return result;
    }

    public void onSubscribe(java.util.concurrent.Flow.Subscription value) {
      subscription = value;
      value.request(1);
    }

    public void onNext(List<java.nio.ByteBuffer> buffers) {
      for (var buffer : buffers) {
        int length = buffer.remaining();
        if (length > limit - received) {
          subscription.cancel();
          result.completeExceptionally(new OperationException("SOURCE_TOO_LARGE", 422));
          return;
        }
        byte[] part = new byte[length];
        buffer.get(part);
        bytes.writeBytes(part);
        received += length;
      }
      subscription.request(1);
    }

    public void onError(Throwable error) {
      result.completeExceptionally(error);
    }

    public void onComplete() {
      result.complete(bytes.toByteArray());
    }
  }
}
