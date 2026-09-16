package dev.modularforge.shared.events;

import java.time.Instant;
import java.util.UUID;

/** Public event contract. Never include email addresses, credentials or tokens. */
public record AccountEvent(UUID eventId, int schemaVersion, String type, Instant occurredAt,
                           String accountRole, Long accountId) {
    public static AccountEvent emailChanged(String role, Long id) {
        return new AccountEvent(UUID.randomUUID(), 1, "account.email-changed", Instant.now(), role, id);
    }
}
