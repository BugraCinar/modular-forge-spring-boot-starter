package dev.modularforge.observability;

import dev.modularforge.identity.model.Admin;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import io.sentry.Sentry;
import io.sentry.protocol.SentryId;

@Profile("dev")
@RestController
@ConditionalOnProperty(prefix = "app.modules.observability", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/admin/sentry")
public class SentryVerificationController {

    @PostMapping("/test")
    public ResponseEntity<Map<String, String>> sendTestEvent() {
        SentryId eventId;

        try {
            throw new IllegalStateException("Sentry integration test event");
        } catch (Exception exception) {
            eventId = Sentry.captureException(exception);
        }

        Sentry.metrics().count("sentry_test", 1.0);
        Sentry.metrics().gauge("sentry_test_queue_size", 42.0);
        Sentry.metrics().distribution("sentry_test_response_time", 150.0);

        return ResponseEntity.ok(Map.of(
                "status", "sent",
                "eventId", eventId.toString()));
    }
}
