package dev.modulithforge.architecture;

import dev.modulithforge.admin.AdminManagementController;
import dev.modulithforge.audit.UserActivityLogger;
import dev.modulithforge.backup.DatabaseBackupController;
import dev.modulithforge.bootstrap.DataInitializer;
import dev.modulithforge.notification.EmailService;
import dev.modulithforge.profile.UserProfileController;
import dev.modulithforge.storage.r2.ImageUploadService;
import dev.modulithforge.twofactor.TwoFactorAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.modules.admin-management.enabled=false",
        "app.modules.user-profile.enabled=false",
        "app.modules.two-factor.enabled=false",
        "app.modules.image-storage.enabled=false",
        "app.modules.audit.enabled=false",
        "app.modules.notification-email.enabled=false",
        "app.modules.database-backup.enabled=false",
        "app.modules.observability.enabled=false"
})
@ActiveProfiles("test")
class DisabledModulesContextTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void optionalModuleBeansAreAbsent() {
        assertThat(context.getBeansOfType(AdminManagementController.class)).isEmpty();
        assertThat(context.getBeansOfType(UserProfileController.class)).isEmpty();
        assertThat(context.getBeansOfType(TwoFactorAuthService.class)).isEmpty();
        assertThat(context.getBeansOfType(ImageUploadService.class)).isEmpty();
        assertThat(context.getBeansOfType(UserActivityLogger.class)).isEmpty();
        assertThat(context.getBeansOfType(EmailService.class)).isEmpty();
        assertThat(context.getBeansOfType(DatabaseBackupController.class)).isEmpty();
        assertThat(context.getBeansOfType(DataInitializer.class)).isEmpty();
    }
}
