package io.tradeops.law;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import io.tradeops.audit.AuditService;
import io.tradeops.error.OperationException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class LawService {
  public record Question(String question, String asOf, List<String> researchAreas) {}

  private final LawClient client;
  private final AuditService audit;
  private final ObjectMapper mapper;

  public LawService(LawClient client, AuditService audit, ObjectMapper mapper) {
    this.client = client;
    this.audit = audit;
    this.mapper = mapper;
  }

  public Object ask(Question input, String actor, String correlation) {
    if (input == null
        || input.question() == null
        || input.question().isBlank()
        || input.question().length() > 4000) throw new OperationException("INVALID_REQUEST", 400);
    if (input.asOf() != null)
      try {
        if (!input.asOf().matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException();
        LocalDate.parse(input.asOf());
      } catch (RuntimeException e) {
        throw new OperationException("INVALID_REQUEST", 400);
      }
    var areas = input.researchAreas() == null ? List.<String>of() : input.researchAreas();
    if (areas.size() > 2
        || new HashSet<>(areas).size() != areas.size()
        || areas.stream()
            .anyMatch(
                a -> a == null || !Set.of("STRATEGIC_GOODS", "DEFENSE_MATERIALS").contains(a)))
      throw new OperationException("INVALID_REQUEST", 400);
    var trace = new LinkedHashMap<String, Object>();
    try {
      var raw = client.ask(new Question(input.question(), input.asOf(), areas));
      String status = raw.path("status").asText();
      require(Set.of("OK", "LOW_CONFIDENCE", "INSUFFICIENT_INFO", "FAILED").contains(status));
      String requestId = raw.path("diagnostics").path("requestId").asText();
      try {
        UUID.fromString(requestId);
      } catch (RuntimeException e) {
        invalid();
      }
      trace.put("ragRequestId", requestId);
      var basis = raw.path("effectiveBasis");
      require(
          basis.isObject()
              && basis.has("snapshotVersion")
              && basis.has("indexedAt")
              && basis.has("sourcePath")
              && basis.has("asOf"));
      trace.put(
          "effectiveBasis",
          select(basis, "snapshotVersion", "indexedAt", "sourcePath", "sourceVersion", "asOf"));
      var citations = raw.path("citedArticles");
      require(citations.isArray());
      var stats = raw.path("diagnostics").path("retrievalStats");
      if (status.equals("OK")
          || (status.equals("LOW_CONFIDENCE")
              && (stats.path("retrieved").asInt() > 0 || stats.path("hydrated").asInt() > 0)))
        require(!citations.isEmpty());
      var out = mapper.createObjectNode();
      out.put("status", status);
      out.put("requestId", requestId);
      out.put("correlationId", correlation);
      out.set("effectiveBasis", select(basis, "asOf"));
      if (status.equals("FAILED")) {
        out.put("message", "법령 검색을 완료하지 못했습니다. 잠시 후 다시 검색해 주세요.");
        out.set("citedArticles", mapper.createArrayNode());
      } else {
        require(
            raw.path("reasoning").isTextual()
                && raw.path("disclaimer").isTextual()
                && raw.path("followUpQuestions").isArray());
        for (var question : raw.path("followUpQuestions")) require(question.isTextual());
        out.set("reasoning", raw.get("reasoning"));
        out.set("disclaimer", raw.get("disclaimer"));
        out.set("followUpQuestions", raw.get("followUpQuestions"));
        var safe = mapper.createArrayNode();
        for (var item : citations) safe.add(article(item, true));
        out.set("citedArticles", safe);
      }
      trace.put("status", status);
      trace.put("success", !status.equals("FAILED"));
      audit.record(actor, "LAW_SEARCH_EXECUTED", "LAW_SEARCH", null, correlation, trace);
      return out;
    } catch (OperationException e) {
      trace.put("success", false);
      trace.put("code", e.code());
      audit.record(actor, "LAW_SEARCH_EXECUTED", "LAW_SEARCH", null, correlation, trace);
      throw e;
    }
  }

  public Object history(long id) {
    positive(id);
    var raw = client.history(id);
    require(raw.path("entries").isArray() && raw.path("lawTitle").isTextual());
    var out = select(raw, "lawId", "lawTitle", "articleNumber");
    var entries = mapper.createArrayNode();
    for (var item : raw.get("entries")) entries.add(article(item, false));
    out.set("entries", entries);
    return out;
  }

  public Object diff(long id, long previous) {
    positive(id);
    positive(previous);
    var raw = client.diff(id, previous);
    require(
        raw.path("articleIdA").asLong() == id
            && raw.path("articleIdB").asLong() == previous
            && raw.path("contentA").isTextual()
            && raw.path("contentB").isTextual()
            && raw.path("contentHashEqual").isBoolean());
    return select(
        raw,
        "articleIdA",
        "articleIdB",
        "lawId",
        "lawTitle",
        "articleNumber",
        "contentA",
        "contentB",
        "contentHashEqual");
  }

  private ObjectNode article(JsonNode raw, boolean cited) {
    require(
        raw.path("articleId").isIntegralNumber()
            && raw.path("articleId").asLong() > 0
            && raw.path("content").isTextual()
            && raw.path("articleNumber").isTextual());
    if (cited) require(raw.path("lawTitle").isTextual());
    var out =
        select(
            raw,
            "articleId",
            "lawId",
            "lawTitle",
            "articleNumber",
            "articleTitle",
            "content",
            "contentHash",
            "effectiveFrom",
            "effectiveTo",
            "amendmentKind",
            "previousArticleId",
            "current");
    var entries = mapper.createArrayNode();
    var history = raw.path("historicalEntries");
    if (!history.isMissingNode() && !history.isNull()) {
      require(history.isArray());
      for (var entry : history) entries.add(article(entry, false));
    }
    if (cited) out.set("historicalEntries", entries);
    return out;
  }

  private ObjectNode select(JsonNode raw, String... fields) {
    var out = mapper.createObjectNode();
    for (String field : fields) out.set(field, raw.has(field) ? raw.get(field) : NullNode.instance);
    return out;
  }

  private void positive(long id) {
    if (id <= 0) throw new OperationException("INVALID_REQUEST", 400);
  }

  private void require(boolean valid) {
    if (!valid) invalid();
  }

  private void invalid() {
    throw new OperationException("LAW_INVALID_RESPONSE", 502);
  }
}
