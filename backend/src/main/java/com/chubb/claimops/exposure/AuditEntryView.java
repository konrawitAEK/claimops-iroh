package com.chubb.claimops.exposure;

import java.time.Instant;
import java.util.UUID;

/** One entry in a claim's audit trail. */
public record AuditEntryView(UUID eventId, String eventType, String previousStatus, String newStatus,
                             String actor, Instant occurredAt) {
}
