package io.tradeops.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AccountBootstrap implements ApplicationRunner {
  private final AccountRepository accounts;
  private final PasswordEncoder encoder;
  private final String owner, password;

  public AccountBootstrap(
      AccountRepository accounts,
      PasswordEncoder encoder,
      @Value("${tradeops.owner.username:admin@tradeops.test}") String owner,
      @Value("${tradeops.owner.password:}") String password) {
    this.accounts = accounts;
    this.encoder = encoder;
    this.owner = owner;
    this.password = password;
  }

  public void run(ApplicationArguments args) {
    if (accounts.find(owner).isEmpty() && !password.isBlank()) {
      AccountService.validatePassword(password);
      accounts.create(owner, encoder.encode(password), false);
    }
  }
}
