package dev.modularforge.auth;

import dev.modularforge.auth.token.*;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.UserRepository;
import dev.modularforge.shared.error.BadRequestException;
import dev.modularforge.shared.notification.NotificationGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EmailChangeService {
    private final UserRepository users;
    private final AdminRepository admins;
    private final PasswordService passwords;
    private final VerificationTokenRepository tokens;
    private final TokenHashService hashes;
    private final NotificationGateway notifications;
    private final RefreshTokenService sessions;
    private final org.springframework.context.ApplicationEventPublisher events;

    @Transactional
    public void request(Long accountId, String role, String currentPassword, String requestedEmail) {
        String email = requestedEmail.trim().toLowerCase(Locale.ROOT);
        String previousEmail;
        String displayName;
        long authVersion;
        if ("admin".equals(role)) {
            var admin = admins.findById(accountId).orElseThrow(() -> new BadRequestException("Account not found"));
            if (!Boolean.TRUE.equals(admin.getIsActive()) || !passwords.verifyPassword(currentPassword, admin.getSalt(), admin.getPasswordHash())) {
                throw new BadRequestException("Current password is incorrect");
            }
            previousEmail = admin.getEmail(); displayName = admin.getUsername(); authVersion = admin.currentAuthVersion();
        } else if ("user".equals(role)) {
            var user = users.findById(accountId).orElseThrow(() -> new BadRequestException("Account not found"));
            if (!Boolean.TRUE.equals(user.getIsActive()) || !passwords.verifyPassword(currentPassword, user.getSalt(), user.getPasswordHash())) {
                throw new BadRequestException("Current password is incorrect");
            }
            previousEmail = user.getEmail(); displayName = user.getUsername(); authVersion = user.currentAuthVersion();
        } else throw new BadRequestException("Invalid account type");
        if (email.equalsIgnoreCase(previousEmail) || users.existsByEmail(email) || admins.existsByEmail(email)) {
            throw new BadRequestException("Email is unchanged or already in use");
        }
        String purpose = role + "_email_change";
        tokens.deleteByUserIdAndRole(accountId, purpose);
        String raw = hashes.generateToken();
        VerificationToken token = new VerificationToken(accountId, purpose);
        token.setRequestedEmail(email);
        token.setIssuedAuthVersion(authVersion);
        token.storeTokenMetadata(raw, hashes.hashToken(raw), hashes.preview(raw));
        tokens.save(token);
        notifications.sendEmailChangeVerificationEmail(email, raw, displayName);
        notifications.sendEmailChangeNotice(previousEmail, email);
    }

    @Transactional
    public void confirm(String raw) {
        if (raw == null || raw.isBlank()) throw new BadRequestException("Invalid or expired email change token");
        VerificationToken token = tokens.findByTokenHash(hashes.hashToken(raw))
                .orElseThrow(() -> new BadRequestException("Invalid or expired email change token"));
        String email = token.getRequestedEmail();
        if (token.isExpired() || email == null || token.getIssuedAuthVersion() == null) {
            throw new BadRequestException("Invalid or expired email change token");
        }
        if (users.existsByEmail(email) || admins.existsByEmail(email)) throw new BadRequestException("Email is already in use");
        String role;
        if ("admin_email_change".equals(token.getRole())) {
            role = "admin";
            var admin = admins.findById(token.getUserId()).orElseThrow(() -> new BadRequestException("Account not found"));
            if (!Boolean.TRUE.equals(admin.getIsActive()) || token.getIssuedAuthVersion() != admin.currentAuthVersion()) throw new BadRequestException("Email change has been invalidated");
            admin.setEmail(email); admin.invalidateAccessTokens(); admins.saveAndFlush(admin);
        } else if ("user_email_change".equals(token.getRole())) {
            role = "user";
            var user = users.findById(token.getUserId()).orElseThrow(() -> new BadRequestException("Account not found"));
            if (!Boolean.TRUE.equals(user.getIsActive()) || token.getIssuedAuthVersion() != user.currentAuthVersion()) throw new BadRequestException("Email change has been invalidated");
            user.setEmail(email); user.setPendingEmail(null); user.setEmailVerified(true);
            user.invalidateAccessTokens(); users.saveAndFlush(user);
        } else throw new BadRequestException("Invalid token type");
        sessions.revokeAllUserTokens(token.getUserId(), role);
        tokens.delete(token);
        events.publishEvent(dev.modularforge.shared.events.AccountEvent.emailChanged(role, token.getUserId()));
    }
}
