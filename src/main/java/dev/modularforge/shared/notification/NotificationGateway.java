package dev.modularforge.shared.notification;

public interface NotificationGateway {

    void sendVerificationEmail(String to, String token, String name);

    void sendPasswordResetEmail(String to, String token, String name);

    void sendEmailChangeVerificationEmail(String to, String token, String name);

    void sendSystemNotificationEmail(String subject, String body);
}
