package com.evplatform.service;

import com.evplatform.domain.AuditLog;
import com.evplatform.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AuditLog logAction(String actorUsername, String actorRole, String action, String entityName, Long entityId, String details, String ipAddress) {
        AuditLog log = new AuditLog(actorUsername, actorRole, action, entityName, entityId, details, ipAddress);
        return auditLogRepository.save(log);
    }

    public List<AuditLog> getRecentLogs() {
        return auditLogRepository.findTop100ByOrderByTimestampDesc();
    }
}
