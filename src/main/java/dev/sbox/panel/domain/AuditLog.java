package dev.sbox.panel.domain;

import java.time.Instant;

public record AuditLog(
        long id,
        Instant createdAt,
        AuditLevel level,
        String actor,
        String action,
        String targetType,
        Long targetId,
        String targetName,
        String message,
        String details
) {
}
