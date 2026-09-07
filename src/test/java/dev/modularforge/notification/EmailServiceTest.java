package dev.modularforge.notification;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock JavaMailSender mailSender;
    @Mock TemplateEngine templateEngine;

    private EmailService service;

    @BeforeEach
    void setUp() {
        service = new EmailService(mailSender, templateEngine);
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@example.com");
        ReflectionTestUtils.setField(service, "apiUrl", "https://api.example.com");
        ReflectionTestUtils.setField(service, "adminEmail", "admin@example.com");
        lenient().when(templateEngine.process(anyString(), any())).thenReturn("<html>message</html>");
    }

    @Test
    void sendsEverySupportedNotification() {
        when(mailSender.createMimeMessage()).thenAnswer(call -> new MimeMessage((Session) null));

        service.sendVerificationEmail("user@example.com", "verify-token", "Ada");
        service.sendPasswordResetEmail("user@example.com", "reset-token", "Ada");
        service.sendPasswordResetSuccessEmail("user@example.com", "Ada", null);
        service.sendPasswordResetSuccessEmail("user@example.com", "Ada", "127.0.0.1");
        service.sendEmailChangeVerificationEmail("new@example.com", "change-token", "Ada");
        service.sendSystemNotificationEmail("Subject", "<p>Body</p>");

        verify(mailSender, times(6)).send(any(MimeMessage.class));
        verify(templateEngine).process(org.mockito.ArgumentMatchers.eq("verification-email"), any());
        verify(templateEngine).process(org.mockito.ArgumentMatchers.eq("password-reset-email"), any());
        verify(templateEngine, times(2)).process(org.mockito.ArgumentMatchers.eq("password-reset-success-email"), any());
        verify(templateEngine).process(org.mockito.ArgumentMatchers.eq("email-change-verification"), any());
    }

    @Test
    void messagingFailuresAreContainedByEveryAsyncBoundary() throws Exception {
        MimeMessage failingMessage = org.mockito.Mockito.mock(MimeMessage.class);
        doThrow(new MessagingException("invalid message")).when(failingMessage).setFrom(any(Address.class));
        when(mailSender.createMimeMessage()).thenReturn(failingMessage);

        assertThatCode(() -> service.sendVerificationEmail("user@example.com", "token", "Ada")).doesNotThrowAnyException();
        assertThatCode(() -> service.sendPasswordResetEmail("user@example.com", "token", "Ada")).doesNotThrowAnyException();
        assertThatCode(() -> service.sendPasswordResetSuccessEmail("user@example.com", "Ada", "127.0.0.1")).doesNotThrowAnyException();
        assertThatCode(() -> service.sendEmailChangeVerificationEmail("new@example.com", "token", "Ada")).doesNotThrowAnyException();
        assertThatCode(() -> service.sendSystemNotificationEmail("subject", "body")).doesNotThrowAnyException();

        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
