package dev.modularforge.bootstrap;

import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.User;
import dev.modularforge.identity.model.UserType;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.UserRepository;
import dev.modularforge.auth.PasswordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.data.init-users", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final PasswordService passwordService;

    @Value("${app.data.admin-username:local_admin}")
    private String adminUsername;

    @Value("${app.data.admin-email:admin@example.com}")
    private String adminEmail;

    @Value("${app.data.admin-password:}")
    private String adminPassword;

    @Value("${app.data.user-username:local_user}")
    private String userUsername;

    @Value("${app.data.user-email:user@example.com}")
    private String userEmail;

    @Value("${app.data.user-password:}")
    private String userPassword;

    @Override
    @Transactional
    public void run(String... args) {
        requireStrongSeedPassword(adminPassword, "app.data.admin-password");
        requireStrongSeedPassword(userPassword, "app.data.user-password");
        log.info("Creating explicitly configured development accounts");

        createAdminUser();
        createUser();

        log.info("Development account setup complete");
    }

    private void createAdminUser() {
        String username = adminUsername;
        String email = adminEmail;

        if (adminRepository.existsByUsername(username)) {
            log.info("Admin '{}' already exists, skipping creation", username);
            return;
        }

        if (adminRepository.existsByEmail(email)) {
            log.info("Admin with email '{}' already exists, skipping creation", email);
            return;
        }

        String salt = passwordService.generateSalt();
        String passwordHash = passwordService.hashPassword(adminPassword, salt);

        Admin admin = new Admin();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setSalt(salt);
        admin.setPasswordHash(passwordHash);
        admin.setFirstName("Super");
        admin.setLastName("Admin");
        admin.setLevel(0);
        admin.setIsActive(true);
        admin.setLoginAttempts(0);
        admin.setPermissions(List.of("ALL"));

        adminRepository.save(admin);

        log.info("Created local level-0 admin '{}' ({})", username, email);
    }

    private void createUser() {
        String username = userUsername;
        String email = userEmail;

        if (userRepository.existsByUsername(username)) {
            log.info("User '{}' already exists, skipping creation", username);
            return;
        }

        if (userRepository.existsByEmail(email)) {
            log.info("User with email '{}' already exists, skipping creation", email);
            return;
        }

        String salt = passwordService.generateSalt();
        String passwordHash = passwordService.hashPassword(userPassword, salt);

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setSalt(salt);
        user.setPasswordHash(passwordHash);
        user.setFirstName("Demo");
        user.setLastName("User");
        user.setUserType(UserType.APP_USER);
        user.setIsActive(true);
        user.setEmailVerified(true);
        user.setLoginAttempts(0);
        user.setBio("Demo app_user account for testing purposes.");

        userRepository.save(user);

        log.info("Created local user '{}' ({})", username, email);
    }

    private void requireStrongSeedPassword(String password, String property) {
        if (password == null || password.length() < 12) {
            throw new IllegalStateException(property + " must contain at least 12 characters when seed data is enabled");
        }
    }
}
