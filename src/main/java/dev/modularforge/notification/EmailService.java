package dev.modularforge.notification;

import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.User;
import dev.modularforge.shared.notification.NotificationGateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@ConditionalOnProperty(prefix = "app.modules.notification-email", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class EmailService implements NotificationGateway {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.api.url}")
    private String apiUrl;

    @Value("${app.admin.email:admin@example.com}")
    private String adminEmail;

    @Async("emailExecutor")
    public void sendVerificationEmail(String to, String token, String name) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Email Verification");
            String verificationUrl = apiUrl + "/api/v1/auth/verify-email?token=" + token;
            Context context = new Context();
            context.setVariable("name", name);
            context.setVariable("verificationUrl", verificationUrl);
            String htmlContent = templateEngine.process("verification-email", context);
            helper.setText(htmlContent, true);
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            log.error("Failed to send verification email to {}: {}", to, e.getMessage(), e);
        }
    }

    @Async("emailExecutor")
    public void sendPasswordResetEmail(String to, String token, String name) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Password Reset Request");
            String resetUrl = apiUrl + "/api/v1/auth/reset-password?token=" + token;
            Context context = new Context();
            context.setVariable("name", name);
            context.setVariable("resetUrl", resetUrl);
            context.setVariable("expiryMinutes", 15); // Match your token expiry time
            String htmlContent = templateEngine.process("password-reset-email", context);
            helper.setText(htmlContent, true);
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            log.error("Failed to send password reset email to {}: {}", to, e.getMessage(), e);
        }
    }

    @Async("emailExecutor")
    public void sendPasswordResetSuccessEmail(String to, String name, String ipAddress) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Şifreniz Başarıyla Değiştirildi - KSS");
            Context context = new Context();
            context.setVariable("name", name);
            context.setVariable("timestamp", java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm",
                java.util.Locale.forLanguageTag("tr-TR"))));
            context.setVariable("currentDate", java.time.LocalDate.now().format(
                java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy",
                java.util.Locale.forLanguageTag("tr-TR"))));
            context.setVariable("ipAddress", ipAddress != null ? ipAddress : "Bilinmiyor");
            String htmlContent = templateEngine.process("password-reset-success-email", context);
            helper.setText(htmlContent, true);
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            log.error("Failed to send password reset success email to {}: {}", to, e.getMessage(), e);
        }
    }
    @Async("emailExecutor")
    public void sendEmailChangeVerificationEmail(String newEmail, String token, String name) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(newEmail);
            helper.setSubject("E-posta Adresinizi Doğrulayın");

            String verificationUrl = apiUrl + "/api/v1/auth/verify-email-change?token=" + token;

            Context context = new Context();
            context.setVariable("name", name);
            context.setVariable("newEmail", newEmail);
            context.setVariable("verificationUrl", verificationUrl);

            String htmlContent = templateEngine.process("email-change-verification", context);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            log.info("Email change verification email sent to {}", newEmail);
        } catch (MessagingException e) {
            log.error("Failed to send email change verification email to {}: {}", newEmail, e.getMessage(), e);
        }
    }
    @Async("emailExecutor")
    public void sendSystemNotificationEmail(String subject, String body) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(adminEmail);
            helper.setSubject(subject);
            helper.setText(body, true); // true indicates HTML content

            mailSender.send(mimeMessage);
            log.info("System notification email sent successfully to admin");
        } catch (MessagingException e) {
            log.error("Failed to send system notification email: {}", e.getMessage(), e);
        }
    }

}
