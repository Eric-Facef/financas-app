package com.eric.financas.audit;

import com.eric.financas.common.web.PageResponse;
import com.eric.financas.audit.dto.AuditLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Registra a auditoria publicando um evento. O gravador (AuditEventListener) só persiste
 * DEPOIS do commit da transação de negócio, então nada é auditado se a operação falhar.
 * Sem transação ativa (ex.: login), grava imediatamente.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final ApplicationEventPublisher publisher;
    private final AuditLogRepository repository;

    public void record(UUID userId, AuditAction action, String entity, String entityId, Map<String, String> details) {
        publisher.publishEvent(new AuditEvent(userId, action, entity, entityId, details, currentIp(), Instant.now()));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(UUID userId, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return PageResponse.from(repository.findByUserId(userId, pageable), AuditLogResponse::from);
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();
        }
        return null;
    }
}
