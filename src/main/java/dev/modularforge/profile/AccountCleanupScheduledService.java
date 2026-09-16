package dev.modularforge.profile;


import dev.modularforge.identity.model.User;
import dev.modularforge.auth.token.RefreshTokenRepository;
import dev.modularforge.identity.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
@Service
@ConditionalOnProperty(prefix = "app.modules.user-profile", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class AccountCleanupScheduledService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ObjectProvider<ProfileImageStorage> imageStorage;

    @Value("${app.account.deactivation.grace-period-days:30}")
    private int gracePeriodDays;

    @Scheduled(cron = "0 30 2 * * *", zone = "Europe/Istanbul")
    @Transactional
    public void anonymiseExpiredAccounts() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(gracePeriodDays);
        log.info("Starting account cleanup. Anonymising accounts deactivated before {}", cutoff);
        List<User> expired = userRepository
                .findByIsActiveFalseAndAdminDeactivatedFalseAndAnonymisedAtIsNullAndDeactivatedAtBefore(cutoff);

        if (expired.isEmpty()) {
            log.info("Account cleanup: no expired accounts found.");
            return;
        }

        int count = 0;
        for (User user : expired) {
            try {
                anonymise(user);
                count++;
            } catch (Exception e) {
                log.error("Failed to anonymise userId={}: {}", user.getId(), e.getMessage(), e);
            }
        }

        log.info("Account cleanup completed. Anonymised {} account(s).", count);
    }

    private void anonymise(User user) {
        log.info("Anonymising expired account userId={}", user.getId());
        if (user.getProfilePicture() != null && !user.getProfilePicture().isBlank()) {
            try {
                imageStorage.orderedStream().forEach(storage -> storage.delete(user.getProfilePicture()));
            } catch (Exception e) {
                log.warn("Could not delete profile picture for userId={}: {}", user.getId(), e.getMessage());
            }
        }
        refreshTokenRepository.revokeAllUserTokens(user.getId(), "user");
        user.setUsername("deleted_" + user.getId());
        user.setEmail("deleted_" + user.getId() + "@deleted.invalid");
        user.setFirstName(null);
        user.setLastName(null);
        user.setPhone(null);
        user.setBio(null);
        user.setProfilePicture(null);
        user.setPasswordHash("ANONYMISED");
        user.setSalt("ANONYMISED");
        user.setAnonymisedAt(LocalDateTime.now());

        userRepository.save(user);
        log.info("Anonymisation complete for userId={}", user.getId());
    }
}
