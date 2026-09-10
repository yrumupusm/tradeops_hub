package io.tradeops.law;

import com.fasterxml.jackson.databind.*;
import io.tradeops.error.OperationException;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LawClient {
  private final URI base;
  private final int timeout;
  private final ObjectMapper mapper;
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public LawClient(
      @Value("${tradeops.law.base-url:}") String url,
      @Value("${tradeops.law.timeout-seconds:180}") int timeout,
      ObjectMapper mapper) {
    this.mapper = mapper;
    if (timeout < 1 || timeout > 180) throw new IllegalArgumentException("LAW_TIMEOUT_INVALID");
    this.timeout = timeout;
    this.base = url.isBlank() ? null : URI.create(url);
    if (base != null
        && (!List.of("http", "https").contains(base.getScheme())
            || base.getHost() == null
            || base.getUserInfo() != null
            || base.getQuery() != null
            || base.getFragment() != null
            || !(base.getPath().isEmpty() || base.getPath().equals("/"))))
      throw new IllegalArgumentException("LAW_BASE_URL_INVALID");
  }

  public JsonNode ask(Object body) {
    return call("/api/ask", body);
  }

  public JsonNode history(long id) {
    return call("/api/articles/" + id + "/history", null);
  }

  public JsonNode diff(long id, long previous) {
    return call("/api/articles/" + id + "/diff?compareWith=" + previous, null);
  }

  private JsonNode call(String path, Object body) {
    if (base == null) throw new OperationException("LAW_UNAVAILABLE", 503);
    CompletableFuture<HttpResponse<byte[]>> pending = null;
    try {
      var request =
          HttpRequest.newBuilder(base.resolve(path))
              .header("Accept", "application/json")
              .timeout(Duration.ofSeconds(timeout));
      if (body != null)
        request
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)));
      pending = http.sendAsync(request.build(), info -> new LimitedBody());
      var response = pending.get(timeout, TimeUnit.SECONDS);
      int status = response.statusCode();
      if (status == 404) throw new OperationException("LAW_ARTICLE_NOT_FOUND", 404);
      if (status == 400) throw new OperationException("LAW_REQUEST_REJECTED", 502);
      if (status != 200) throw new OperationException("LAW_UNAVAILABLE", 503);
      var value = mapper.readTree(response.body());
      if (value == null || !value.isObject())
        throw new OperationException("LAW_INVALID_RESPONSE", 502);
      return value;
    } catch (TimeoutException e) {
      throw new OperationException("LAW_TIMEOUT", 504);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OperationException("LAW_UNAVAILABLE", 503);
    } catch (ExecutionException e) {
      throw new OperationException(
          e.getCause() instanceof HttpTimeoutException ? "LAW_TIMEOUT" : "LAW_UNAVAILABLE",
          e.getCause() instanceof HttpTimeoutException ? 504 : 503);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new OperationException("LAW_INVALID_RESPONSE", 502);
    } catch (java.io.IOException e) {
      throw new OperationException("LAW_INVALID_RESPONSE", 502);
    } finally {
      if (pending != null && !pending.isDone()) pending.cancel(true);
    }
  }

  private static class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final HttpResponse.BodySubscriber<byte[]> delegate =
        HttpResponse.BodySubscribers.ofByteArray();
    private Flow.Subscription subscription;
    private long size;

    public CompletionStage<byte[]> getBody() {
      return delegate.getBody();
    }

    public void onSubscribe(Flow.Subscription value) {
      subscription = value;
      delegate.onSubscribe(value);
    }

    public void onNext(List<ByteBuffer> values) {
      for (var value : values) size += value.remaining();
      if (size > 4 * 1024 * 1024) {
        subscription.cancel();
        delegate.onError(new java.io.IOException("LAW_RESPONSE_TOO_LARGE"));
      } else delegate.onNext(values);
    }

    public void onError(Throwable error) {
      delegate.onError(error);
    }

    public void onComplete() {
      delegate.onComplete();
    }
  }
}
