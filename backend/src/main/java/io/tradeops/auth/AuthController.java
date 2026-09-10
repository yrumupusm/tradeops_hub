package io.tradeops.auth;

import io.tradeops.account.AccountService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AccountService accounts;

  public AuthController(AccountService accounts) {
    this.accounts = accounts;
  }

  @GetMapping("/csrf")
  public Object csrf(CsrfToken token) {
    return java.util.Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @PostMapping("/login")
  public Object login(
      @Valid @RequestBody Login r, HttpServletRequest req, HttpServletResponse res) {
    var user =
        accounts.login(
            r.username(),
            r.password(),
            req.getRemoteAddr(),
            req.getAttribute("correlationId").toString());
    if (req.getSession(false) != null) req.changeSessionId();
    else req.getSession(true);
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(user.username(), null, List.of()));
    SecurityContextHolder.setContext(context);
    new HttpSessionSecurityContextRepository().saveContext(context, req, res);
    return accounts.view(user.username());
  }

  @GetMapping("/me")
  public Object me(Authentication auth) {
    return accounts.view(auth.getName());
  }

  @PostMapping("/logout")
  public void logout(Authentication auth, HttpServletRequest req) {
    accounts.logout(auth.getName(), req.getAttribute("correlationId").toString());
    if (req.getSession(false) != null) req.getSession().invalidate();
    SecurityContextHolder.clearContext();
  }

  public record Login(
      @NotBlank @Size(max = 120) String username,
      @NotBlank @Size(min = 8, max = 160) String password) {}
}
