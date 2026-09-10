package io.tradeops.account;

import io.tradeops.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AccountController {
  private final AccountService service;
  private final AuditService audit;

  public AccountController(AccountService service, AuditService audit) {
    this.service = service;
    this.audit = audit;
  }

  private String correlation(HttpServletRequest r) {
    return r.getAttribute("correlationId").toString();
  }

  @GetMapping("/users")
  public Object list(Authentication a) {
    return service.list(a.getName());
  }

  @PostMapping("/users")
  public void create(Authentication a, @RequestBody Create r, HttpServletRequest req) {
    service.create(a.getName(), r.username(), r.password(), correlation(req));
  }

  @PatchMapping("/users/{id}")
  public void state(
      Authentication a,
      @PathVariable long id,
      @Valid @RequestBody State r,
      HttpServletRequest req) {
    service.state(a.getName(), id, r.enabled(), correlation(req));
  }

  @PostMapping("/users/{id}/reset-password")
  public void reset(
      Authentication a, @PathVariable long id, @RequestBody Password r, HttpServletRequest req) {
    service.reset(a.getName(), id, r.password(), correlation(req));
  }

  @PostMapping("/account/password")
  public void change(Authentication a, @Valid @RequestBody Change r, HttpServletRequest req) {
    service.change(a.getName(), r.currentPassword(), r.newPassword(), correlation(req));
    if (req.getSession(false) != null) req.getSession().invalidate();
  }

  @GetMapping("/audit-events")
  public Object audit(
      Authentication a,
      @RequestParam(defaultValue = "") String actor,
      @RequestParam(defaultValue = "") String event,
      @RequestParam(defaultValue = "") String from,
      @RequestParam(defaultValue = "") String to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    service.requireOwner(a.getName());
    return audit.list(actor, event, from, to, page, size);
  }

  public record Create(String username, String password) {}

  public record State(@NotNull Boolean enabled) {}

  public record Password(String password) {}

  public record Change(@NotBlank String currentPassword, @NotBlank String newPassword) {}
}
