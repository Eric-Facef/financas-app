package com.eric.financas.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEvent(
        UUID userId,
        AuditAction action,
        String entity,
        String entityId,
        Map<String, String> details,
        String ip,
        Instant occurredAt
) {
}
