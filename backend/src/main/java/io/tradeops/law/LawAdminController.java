package io.tradeops.law;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/law-admin")
public class LawAdminController {
  private final LawAdminService service;

  public LawAdminController(LawAdminService service) {
    this.service = service;
  }

  @PostMapping("/actions/{action}")
  public Object execute(
      Authentication actor,
      @PathVariable String action,
      @RequestBody java.util.Map<String, Object> body,
      jakarta.servlet.http.HttpServletRequest request) {
    if (!body.isEmpty()) throw new io.tradeops.error.OperationException("INVALID_REQUEST", 400);
    return service.execute(
        actor.getName(), action, String.valueOf(request.getAttribute("correlationId")));
  }

  @GetMapping("/{section}")
  public Object read(
      Authentication actor,
      @PathVariable String section,
      @RequestParam(defaultValue = "") String requestId,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int page) {
    return service.read(actor.getName(), section, requestId, q, page);
  }

  @GetMapping("/laws/{id}")
  public Object law(Authentication actor, @PathVariable long id) {
    return service.law(actor.getName(), id, false);
  }

  @GetMapping("/laws/{id}/revisions")
  public Object revisions(Authentication actor, @PathVariable long id) {
    return service.law(actor.getName(), id, true);
  }
}
