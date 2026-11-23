package com.networkproxy.service;

import com.networkproxy.auth.AdminPrincipal;
import com.networkproxy.dto.AuditLogResponse;
import com.networkproxy.dto.PageResponse;
import com.networkproxy.entity.AdminUser;
import com.networkproxy.entity.AuditLog;
import com.networkproxy.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(
            AdminPrincipal principal,
            String action,
            String entityType,
            Long entityId,
            String details
    ) {
        try {
            auditLogRepository.insert(
                    principal == null ? null : principal.userId(),
                    principal == null ? null : principal.username(),
                    action,
                    entityType,
                    entityId,
                    details);
        } catch (DataAccessException exception) {
            log.warn("Không ghi được audit log: {}", exception.getMessage());
        }
    }

    public void record(
            AdminUser user,
            String action,
            String entityType,
            Long entityId,
            String details
    ) {
        record(new AdminPrincipal(
                        user.userId(),
                        user.username(),
                        user.displayName()),
                action,
                entityType,
                entityId,
                details);
    }

    public PageResponse<AuditLogResponse> list(int page, int size) {
        PageResponse<AuditLog> result = auditLogRepository.findPage(
                Pagination.page(page),
                Pagination.size(size));
        return new PageResponse<>(
                result.content().stream().map(AuditLogResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    public List<AuditLogResponse> recent(int limit) {
        return auditLogRepository.recent(limit).stream()
                .map(AuditLogResponse::from)
                .toList();
    }
}
