package io.tradeops.law;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import io.tradeops.account.AccountService;
import io.tradeops.audit.AuditService;
import io.tradeops.error.OperationException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class LawAdminService {
  private final LawClient client;
  private final AccountService accounts;
  private final AuditService audit;
  private final java.util.concurrent.atomic.AtomicBoolean running =
      new java.util.concurrent.atomic.AtomicBoolean();
  private static final Set<String> FIELDS =
      Set.of(
          "lastSnapshotVersion",
          "lastIndexedAt",
          "indexStatus",
          "lawsCount",
          "articlesCount",
          "indexedArticlesCount",
          "unindexedArticlesCount",
          "searchLogCount",
          "recentFailures",
          "reindexEnabled",
          "total",
          "totalCount",
          "page",
          "size",
          "items",
          "laws",
          "logs",
          "traces",
          "runs",
          "id",
          "lawId",
          "title",
          "lawType",
          "lawNumber",
          "articleCount",
          "revisionCount",
          "snapshotVersion",
          "articles",
          "articleId",
          "articleNumber",
          "articleTitle",
          "content",
          "effectiveFrom",
          "effectiveTo",
          "amendmentKind",
          "revisions",
          "requestId",
          "questionType",
          "status",
          "asOf",
          "citedArticleCount",
          "latencyAnalyzeMs",
          "latencyRetrieveMs",
          "latencySynthesizeMs",
          "createdAt",
          "stepName",
          "latencyMs",
          "ingestionRunId",
          "startedAt",
          "finishedAt",
          "filesProcessed",
          "filesFailed",
          "action",
          "commitHash",
          "ingestion",
          "indexedArticles",
          "failedArticles",
          "lawsImported",
          "articlesImported",
          "runId",
          "llmStatus",
          "embeddingStatus",
          "rerankerStatus",
          "embeddingDimensions",
          "rerankedCount");

  public LawAdminService(LawClient client, AccountService accounts, AuditService audit) {
    this.client = client;
    this.accounts = accounts;
    this.audit = audit;
  }

  public JsonNode execute(String actor, String action, String correlation) {
    accounts.requireOwner(actor);
    if (!Set.of("sync-source", "ingest-local", "reindex", "provider-smoke-test").contains(action))
      throw new OperationException("INVALID_REQUEST", 400);
    if (action.equals("reindex")
        && !client.administration("/api/admin/status").path("reindexEnabled").asBoolean())
      throw new OperationException("LAW_REINDEX_DISABLED", 409);
    if (!running.compareAndSet(false, true)) throw new OperationException("LAW_ADMIN_BUSY", 409);
    try {
      audit.record(
          actor, "LAW_ADMIN_STARTED", "LAW_ADMIN", null, correlation, Map.of("action", action));
      JsonNode result = project(client.administrationAction(action));
      audit.record(
          actor,
          "LAW_ADMIN_FINISHED",
          "LAW_ADMIN",
          null,
          correlation,
          Map.of("action", action, "status", result.path("status").asText("RETURNED")));
      return result;
    } catch (OperationException e) {
      audit.record(
          actor, "LAW_ADMIN_FAILED", "LAW_ADMIN", null, correlation, Map.of("action", action));
      throw e;
    } finally {
      running.set(false);
    }
  }

  public JsonNode read(String actor, String section, String requestId, String q, int page) {
    accounts.requireOwner(actor);
    String path;
    switch (section) {
      case "status", "ingestion-runs", "search-logs" -> path = "/api/admin/" + section;
      case "agent-traces" -> {
        if (!requestId.isEmpty()) {
          try {
            if (!UUID.fromString(requestId).toString().equals(requestId))
              throw new IllegalArgumentException();
          } catch (IllegalArgumentException e) {
            throw new OperationException("INVALID_REQUEST", 400);
          }
        }
        path = "/api/admin/agent-traces" + (requestId.isEmpty() ? "" : "?requestId=" + requestId);
      }
      case "laws" -> {
        if (q.length() > 200 || page < 1 || page > 10000)
          throw new OperationException("INVALID_REQUEST", 400);
        path =
            "/api/laws?page=" + page + "&size=20&q=" + URLEncoder.encode(q, StandardCharsets.UTF_8);
      }
      default -> throw new OperationException("INVALID_REQUEST", 400);
    }
    JsonNode result = client.administration(path);
    if (section.equals("status")) {
      if (!result.path("indexStatus").isTextual() || !result.path("articlesCount").isNumber())
        invalid();
    } else if (!result.path("items").isArray()) invalid();
    return project(result);
  }

  public JsonNode law(String actor, long id, boolean revisions) {
    accounts.requireOwner(actor);
    if (id < 1) throw new OperationException("INVALID_REQUEST", 400);
    JsonNode result = client.administration("/api/laws/" + id + (revisions ? "/revisions" : ""));
    if (!result.path(revisions ? "revisions" : "articles").isArray()) invalid();
    return project(result);
  }

  private void invalid() {
    throw new OperationException("LAW_INVALID_RESPONSE", 502);
  }

  private JsonNode project(JsonNode value) {
    if (value.isArray()) {
      var result = JsonNodeFactory.instance.arrayNode();
      value.forEach(v -> result.add(project(v)));
      return result;
    }
    if (value.isObject()) {
      var result = JsonNodeFactory.instance.objectNode();
      value
          .fields()
          .forEachRemaining(
              e -> {
                if (FIELDS.contains(e.getKey())) result.set(e.getKey(), project(e.getValue()));
              });
      return result;
    }
    return value;
  }
}
