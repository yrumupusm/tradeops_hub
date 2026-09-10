package io.tradeops.account;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
  public record Account(
      long id, String username, String passwordHash, boolean enabled, boolean mustChangePassword) {}

  private final JdbcTemplate jdbc;

  public AccountRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private Account map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
    return new Account(
        rs.getLong("id"),
        rs.getString("username"),
        rs.getString("password_hash"),
        rs.getBoolean("enabled"),
        rs.getBoolean("must_change_password"));
  }

  public Optional<Account> find(String username) {
    return jdbc
        .query("SELECT * FROM app_users WHERE LOWER(username)=LOWER(?)", this::map, username)
        .stream()
        .findFirst();
  }

  public Optional<Account> byId(long id) {
    return jdbc.query("SELECT * FROM app_users WHERE id=?", this::map, id).stream().findFirst();
  }

  public List<Map<String, Object>> list() {
    return jdbc.queryForList(
        "SELECT id,username,enabled,must_change_password,created_at FROM app_users ORDER BY id");
  }

  public void create(String username, String hash, boolean temporary) {
    jdbc.update(
        "INSERT INTO app_users(username,password_hash,must_change_password) VALUES(?,?,?)",
        username,
        hash,
        temporary);
  }

  public void password(long id, String hash, boolean temporary) {
    jdbc.update(
        "UPDATE app_users SET password_hash=?,must_change_password=? WHERE id=?",
        hash,
        temporary,
        id);
  }

  public void enabled(long id, boolean enabled) {
    jdbc.update("UPDATE app_users SET enabled=? WHERE id=?", enabled, id);
  }

  public void revoke(String username) {
    jdbc.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME=?", username);
  }

  public int attempts(String key) {
    List<Integer> values =
        jdbc.query(
            "SELECT failures FROM login_attempts WHERE attempt_key=? AND"
                + " window_started>CURRENT_TIMESTAMP-INTERVAL '10' MINUTE",
            (r, n) -> r.getInt(1),
            key);
    return values.isEmpty() ? 0 : values.get(0);
  }

  public void failure(String key) {
    jdbc.update(
        "DELETE FROM login_attempts WHERE window_started<CURRENT_TIMESTAMP-INTERVAL '10' MINUTE");
    int n = jdbc.update("UPDATE login_attempts SET failures=failures+1 WHERE attempt_key=?", key);
    if (n == 0)
      try {
        jdbc.update("INSERT INTO login_attempts VALUES(?,1,CURRENT_TIMESTAMP)", key);
      } catch (org.springframework.dao.DuplicateKeyException e) {
        jdbc.update("UPDATE login_attempts SET failures=failures+1 WHERE attempt_key=?", key);
      }
  }

  public void clear(String key) {
    jdbc.update("DELETE FROM login_attempts WHERE attempt_key=?", key);
  }
}
