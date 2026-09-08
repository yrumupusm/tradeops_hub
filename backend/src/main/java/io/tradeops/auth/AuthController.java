package io.tradeops.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.tradeops.user.AppUser;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AppUser user = authService.authenticate(request.username(), request.password());
        return new LoginResponse(jwtService.issue(user), "Bearer", jwtService.getExpirationSeconds(), UserResponse.from(user));
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        return new CurrentUserResponse(authentication.getName(), authentication.getAuthorities().iterator().next().getAuthority().replace("ROLE_", ""));
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record LoginRequest(
            @NotBlank @Size(max = 120) String username,
            @NotBlank @Size(min = 8, max = 160) String password
    ) { }

    public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds, UserResponse user) { }
    public record UserResponse(String username, String role) {
        static UserResponse from(AppUser user) { return new UserResponse(user.getUsername(), user.getRole().name()); }
    }
    public record CurrentUserResponse(String username, String role) { }
}
