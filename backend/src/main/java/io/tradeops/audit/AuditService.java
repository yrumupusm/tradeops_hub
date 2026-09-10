package io.tradeops.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
  private final AuditRepository repository;
  private final ObjectMapper mapper;

  public AuditService(AuditRepository repository, ObjectMapper mapper) {
    this.repository = repository;
    this.mapper = mapper;
  }

  public void record(
      String actor, String event, String kind, Object id, String correlation, Object details) {
    try {
      repository.append(
          actor,
          event,
          kind,
          id == null ? null : id.toString(),
          correlation,
          mapper.writeValueAsString(details));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalArgumentException("AUDIT_DETAIL_INVALID");
    }
  }

  public java.util.Map<String, Object> list(
      String actor, String event, String from, String to, int page, int size) {
    if (page < 0 || size < 1 || size > 100)
      throw new io.tradeops.error.OperationException("INVALID_REQUEST", 400);
    try {
      if (!from.isEmpty()) java.time.OffsetDateTime.parse(from);
      if (!to.isEmpty()) java.time.OffsetDateTime.parse(to);
    } catch (java.time.format.DateTimeParseException e) {
      throw new io.tradeops.error.OperationException("INVALID_REQUEST", 400);
    }
    return repository.list(actor, event, from, to, page, size);
  }
}
