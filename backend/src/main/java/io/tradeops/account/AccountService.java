package io.tradeops.account;

import io.tradeops.audit.AuditService;
import io.tradeops.error.OperationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
  private final AccountRepository accounts;
  private final PasswordEncoder passwords;
  private final AuditService audit;
  private final String owner;
  private final String dummy;

  public AccountService(
      AccountRepository accounts,
      PasswordEncoder passwords,
      AuditService audit,
      @Value("${tradeops.owner.username:admin@tradeops.test}") String owner) {
    this.accounts = accounts;
    this.passwords = passwords;
    this.audit = audit;
    this.owner = owner;
    this.dummy = passwords.encode(UUID.randomUUID().toString());
  }

  public boolean isOwner(String actor) {
    return owner.equalsIgnoreCase(actor);
  }

  public void requireOwner(String actor) {
    if (!isOwner(actor)) throw new OperationException("ACCESS_DENIED", 403);
  }

  public AccountRepository.Account get(String username) {
    return accounts
        .find(username)
        .filter(AccountRepository.Account::enabled)
        .orElseThrow(() -> new OperationException("AUTHENTICATION_REQUIRED", 401));
  }

  public Map<String, Object> view(String username) {
    var a = get(username);
    return Map.of(
        "id",
        a.id(),
        "username",
        a.username(),
        "owner",
        isOwner(a.username()),
        "mustChangePassword",
        a.mustChangePassword());
  }

  public AccountRepository.Account login(
      String username, String password, String ip, String correlation) {
    String key = hash(username.toLowerCase(Locale.ROOT) + "|" + ip);
    if (accounts.attempts(key) >= 5) throw new OperationException("LOGIN_RATE_LIMITED", 429);
    var found = accounts.find(username);
    boolean matches =
        passwords.matches(
            password, found.map(AccountRepository.Account::passwordHash).orElse(dummy));
    if (found.isEmpty() || !matches || !found.get().enabled()) {
      accounts.failure(key);
      audit.record(
          found.map(AccountRepository.Account::username).orElse(null),
          "LOGIN_FAILED",
          "ACCOUNT",
          null,
          correlation,
          Map.of("success", false));
      throw new OperationException("AUTHENTICATION_FAILED", 401);
    }
    accounts.clear(key);
    audit.record(
        found.get().username(),
        "LOGIN_SUCCEEDED",
        "ACCOUNT",
        found.get().id(),
        correlation,
        Map.of("success", true));
    return found.get();
  }

  public List<Map<String, Object>> list(String actor) {
    requireOwner(actor);
    return accounts.list();
  }

  @Transactional
  public void create(String actor, String username, String password, String correlation) {
    requireOwner(actor);
    validatePassword(password);
    if (username == null || !username.matches("[A-Za-z0-9@._+-]{3,120}"))
      throw new OperationException("INVALID_REQUEST", 400);
    if (accounts.find(username).isPresent()) throw new OperationException("USERNAME_EXISTS", 409);
    accounts.create(username, passwords.encode(password), true);
    audit.record(
        actor,
        "ACCOUNT_CREATED",
        "ACCOUNT",
        accounts.find(username).orElseThrow().id(),
        correlation,
        Map.of("success", true));
  }

  @Transactional
  public void state(String actor, long id, boolean enabled, String correlation) {
    requireOwner(actor);
    var a = accounts.byId(id).orElseThrow(() -> new OperationException("NOT_FOUND", 404));
    if (isOwner(a.username())) throw new OperationException("OWNER_PROTECTED", 409);
    accounts.enabled(id, enabled);
    if (!enabled) accounts.revoke(a.username());
    audit.record(
        actor,
        "ACCOUNT_STATE_CHANGED",
        "ACCOUNT",
        id,
        correlation,
        Map.of("enabled", enabled, "success", true));
  }

  @Transactional
  public void reset(String actor, long id, String password, String correlation) {
    requireOwner(actor);
    var a = accounts.byId(id).orElseThrow(() -> new OperationException("NOT_FOUND", 404));
    if (isOwner(a.username())) throw new OperationException("OWNER_PROTECTED", 409);
    validatePassword(password);
    accounts.password(id, passwords.encode(password), true);
    accounts.revoke(a.username());
    audit.record(actor, "PASSWORD_RESET", "ACCOUNT", id, correlation, Map.of("success", true));
  }

  @Transactional
  public void change(String actor, String current, String next, String correlation) {
    var a = get(actor);
    if (!passwords.matches(current, a.passwordHash()))
      throw new OperationException("AUTHENTICATION_FAILED", 401);
    validatePassword(next);
    if (passwords.matches(next, a.passwordHash()))
      throw new OperationException("PASSWORD_UNCHANGED", 400);
    accounts.password(a.id(), passwords.encode(next), false);
    accounts.revoke(a.username());
    audit.record(
        actor, "PASSWORD_CHANGED", "ACCOUNT", a.id(), correlation, Map.of("success", true));
  }

  public void logout(String actor, String correlation) {
    audit.record(actor, "LOGOUT", "ACCOUNT", null, correlation, Map.of("success", true));
  }

  public static void validatePassword(String password) {
    if (password == null
        || password.length() < 8
        || password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new OperationException("INVALID_PASSWORD", 400);
  }

  private String hash(String v) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
