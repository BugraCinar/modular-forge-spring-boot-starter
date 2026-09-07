package dev.modularforge.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SentryVerificationControllerTest {

    @Test
    void returnsTheCapturedEventIdentifier() {
        var response = new SentryVerificationController().sendTestEvent();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("status", "sent").containsKey("eventId");
    }
}
