# SMTP notification adapter

Id `notification-email`; package `dev.modulithforge.notification`; default on; toggle `app.modules.notification-email.enabled`.

It implements `shared.notification.NotificationGateway` with Spring Mail and Thymeleaf templates in `src/main/resources/templates`.

## Remove or replace

1. Set the toggle to `false`.
2. Delete the notification package and email templates.
3. Remove mail, Thymeleaf, and Jakarta Mail dependencies only if no other module uses them.
4. Remove `spring.mail.*`, `MAIL_*`, sender, and admin-email settings.
5. Install another `NotificationGateway` before using registration, reset, verification, or email-change flows. The fallback intentionally throws instead of pretending delivery succeeded.
6. Remove the catalog entry only when notifications are no longer a supported extension.
7. Run `./mvnw clean verify` and end-to-end delivery tests for the replacement.
