package dev.modularforge.modules;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;

import static dev.modularforge.modules.ModuleDefinition.ModuleKind.OPTIONAL;
import static dev.modularforge.modules.ModuleDefinition.ModuleKind.REPLACEABLE;
import static dev.modularforge.modules.ModuleDefinition.ModuleKind.REQUIRED;

@Service
public class ModuleCatalogService {

    private final Environment environment;

    public ModuleCatalogService(Environment environment) {
        this.environment = environment;
    }

    public List<ModuleDefinition> catalog() {
        return List.of(
                required("identity", "Identity", "User and admin account records using the " + environment.getProperty("app.database.provider", "sql") + " provider.",
                        "dev.modularforge.identity", List.of()),
                required("authentication", "Authentication", "Login, registration, password and token flows.",
                        "dev.modularforge.auth", List.of("identity", "security", "rate-limit", "notification-email")),
                required("security", "Security", "JWT validation, authorization and HTTP security.",
                        "dev.modularforge.security", List.of("identity", "rate-limit")),
                required("rate-limit", "Rate limiting", "Redis-backed limits shared by every application instance.",
                        "dev.modularforge.ratelimit", List.of()),
                replaceable("notification-email", "Email notifications", "SMTP delivery and email templates.",
                        "dev.modularforge.notification", "app.modules.notification-email.enabled", true,
                        List.of()),
                optional("admin-management", "Admin management", "Admin and user administration endpoints.",
                        "dev.modularforge.admin", "app.modules.admin-management.enabled", true,
                        List.of("identity", "security")),
                optional("user-profile", "User profile", "Self-service profile, email change and account closure.",
                        "dev.modularforge.profile", "app.modules.user-profile.enabled", true,
                        List.of("identity", "authentication")),
                optional("two-factor", "TOTP two-factor authentication", "Encrypted TOTP credentials and login challenges.",
                        "dev.modularforge.twofactor", "app.modules.two-factor.enabled", false,
                        List.of("authentication", "identity")),
                replaceable("image-storage", "Cloudflare R2 image storage", "Profile image upload through the storage port.",
                        "dev.modularforge.storage.r2", "app.modules.image-storage.enabled", false,
                        List.of("user-profile", "admin-management")),
                optional("audit", "Security audit trail", "Admin activity, authentication failures and sensitive-access logs.",
                        "dev.modularforge.audit", "app.modules.audit.enabled", true,
                        List.of("identity", "security")),
                optional("database-backup", "Database backup", "Encrypted MySQL/MariaDB dumps delivered over email.",
                        "dev.modularforge.backup", "app.modules.database-backup.enabled", false,
                        List.of("notification-email", "security")),
                optional("observability", "Sentry observability", "Error reporting and an opt-in verification endpoint.",
                        "dev.modularforge.observability", "app.modules.observability.enabled", false,
                        List.of("security")),
                optional("api-docs", "OpenAPI documentation", "Swagger UI for development environments.",
                        "dev.modularforge.observability", "app.swagger.enabled", false,
                        List.of("security")),
                optional("kafka", "Kafka application events", "Account events published after transaction commit.",
                        "dev.modularforge.kafka", "app.modules.kafka.enabled", false, List.of()),
                optional("seed-data", "Development seed data", "Creates local demo accounts when explicitly enabled.",
                        "dev.modularforge.bootstrap", "app.data.init-users", false,
                        List.of("identity"))
        );
    }

    private ModuleDefinition required(
            String id,
            String displayName,
            String description,
            String packageName,
            List<String> dependencies) {
        return new ModuleDefinition(id, displayName, description, packageName, REQUIRED, true,
                null, dependencies, "docs/modules/" + id + ".md");
    }

    private ModuleDefinition optional(
            String id,
            String displayName,
            String description,
            String packageName,
            String toggle,
            boolean defaultValue,
            List<String> dependencies) {
        return definition(id, displayName, description, packageName, OPTIONAL, toggle, defaultValue, dependencies);
    }

    private ModuleDefinition replaceable(
            String id,
            String displayName,
            String description,
            String packageName,
            String toggle,
            boolean defaultValue,
            List<String> dependencies) {
        return definition(id, displayName, description, packageName, REPLACEABLE, toggle, defaultValue, dependencies);
    }

    private ModuleDefinition definition(
            String id,
            String displayName,
            String description,
            String packageName,
            ModuleDefinition.ModuleKind kind,
            String toggle,
            boolean defaultValue,
            List<String> dependencies) {
        boolean enabled = environment.getProperty(toggle, Boolean.class, defaultValue);
        return new ModuleDefinition(id, displayName, description, packageName, kind, enabled,
                toggle, dependencies, "docs/modules/" + id + ".md");
    }
}
