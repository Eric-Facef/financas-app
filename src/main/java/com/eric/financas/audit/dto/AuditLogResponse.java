package com.eric.financas.audit.dto;

import com.eric.financas.audit.AuditAction;
import com.eric.financas.audit.AuditLog;

import java.time.Instant;
import java.util.Map;

public record AuditLogResponse(
        Long id,
        AuditAction action,
        String entity,
        String entityId,
        Map<String, String> details,
        String ip,
        Instant createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getAction(), log.getEntity(), log.getEntityId(),
                log.getDetails(), log.getIp(), log.getCreatedAt());
    }
}
