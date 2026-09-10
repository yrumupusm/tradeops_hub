package io.tradeops.auth;

import io.tradeops.account.AccountService;
import io.tradeops.error.OperationException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
  @Bean
  SecurityFilterChain chain(
      HttpSecurity http,
      AccountService accounts,
      com.fasterxml.jackson.databind.ObjectMapper mapper)
      throws Exception {
    var csrf = new HttpSessionCsrfTokenRepository();
    csrf.setHeaderName("X-CSRF-TOKEN");
    return http.csrf(
            c ->
                c.csrfTokenRepository(csrf)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .requestCache(c -> c.disable())
        .formLogin(c -> c.disable())
        .httpBasic(c -> c.disable())
        .logout(c -> c.disable())
        .authorizeHttpRequests(
            c ->
                c.requestMatchers(
                        "/api/v1/health",
                        "/actuator/health",
                        "/api/v1/auth/csrf",
                        "/api/v1/auth/login")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            c ->
                c.authenticationEntryPoint(
                        (r, s, e) -> error(r, s, mapper, "AUTHENTICATION_REQUIRED", 401))
                    .accessDeniedHandler(
                        (r, s, e) ->
                            error(
                                r,
                                s,
                                mapper,
                                e instanceof CsrfException ? "CSRF_INVALID" : "ACCESS_DENIED",
                                403)))
        .addFilterBefore(
            new OncePerRequestFilter() {
              protected void doFilterInternal(
                  HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                  throws ServletException, IOException {
                var a = SecurityContextHolder.getContext().getAuthentication();
                if (a != null && a.isAuthenticated() && !"anonymousUser".equals(a.getPrincipal()))
                  try {
                    var user = accounts.get(a.getName());
                    String path = req.getRequestURI();
                    if (user.mustChangePassword()
                        && !java.util.Set.of(
                                "/api/v1/auth/me",
                                "/api/v1/auth/csrf",
                                "/api/v1/auth/logout",
                                "/api/v1/account/password")
                            .contains(path)) {
                      error(req, res, mapper, "PASSWORD_CHANGE_REQUIRED", 403);
                      return;
                    }
                  } catch (OperationException e) {
                    SecurityContextHolder.clearContext();
                    if (req.getSession(false) != null) req.getSession().invalidate();
                    error(req, res, mapper, e.code(), e.status());
                    return;
                  }
                chain.doFilter(req, res);
              }
            },
            AuthorizationFilter.class)
        .build();
  }

  private static void error(
      HttpServletRequest req,
      HttpServletResponse res,
      com.fasterxml.jackson.databind.ObjectMapper mapper,
      String code,
      int status)
      throws IOException {
    res.setStatus(status);
    res.setContentType("application/json");
    mapper.writeValue(
        res.getOutputStream(),
        java.util.Map.of(
            "code",
            code,
            "message",
            "요청을 처리할 수 없습니다.",
            "correlationId",
            String.valueOf(req.getAttribute("correlationId"))));
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  org.springframework.security.core.userdetails.UserDetailsService noDefaultUser() {
    return username -> {
      throw new org.springframework.security.core.userdetails.UsernameNotFoundException(
          "ACCOUNT_LOGIN_REQUIRED");
    };
  }
}
