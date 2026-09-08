package io.tradeops.user;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "tradeops.demo-users", name = "enabled", havingValue = "true")
public class DemoUserSeeder implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public DemoUserSeeder(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<DemoUser> demoUsers = List.of(
                new DemoUser("admin@tradeops.test", Role.ADMIN),
                new DemoUser("operator@tradeops.test", Role.OPERATOR),
                new DemoUser("viewer@tradeops.test", Role.VIEWER)
        );
        for (DemoUser demo : demoUsers) {
            if (users.findByUsernameIgnoreCase(demo.username()).isEmpty()) {
                users.save(new AppUser(demo.username(), passwordEncoder.encode("portfolio-demo"), demo.role(), true));
            }
        }
    }

    private record DemoUser(String username, Role role) { }
}
