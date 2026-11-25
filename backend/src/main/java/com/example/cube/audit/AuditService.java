package com.example.cube.audit;

import com.example.cube.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Records security-relevant and administrative actions in CU_AUDIT_LOG.
 * Joins the caller's transaction, so an action that rolls back leaves no audit row.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(String action, String entityType, Object entityId, String details) {
        recordAs(currentActor(), action, entityType, entityId, details);
    }

    public void recordAs(String actor, String action, String entityType, Object entityId, String details) {
        try {
            AuditLog entry = new AuditLog();
            entry.setActor(truncate(actor, 50));
            entry.setAction(truncate(action, 50));
            entry.setEntityType(truncate(entityType, 50));
            entry.setEntityId(entityId == null ? null : truncate(String.valueOf(entityId), 100));
            entry.setDetails(truncate(details, 1000));
            entry.setCreatedAt(LocalDateTime.now());
            repository.save(entry);
        } catch (RuntimeException ex) {
            // Auditing must never break the business operation.
            log.warn("Could not write audit entry {} {} {}: {}", action, entityType, entityId, ex.getMessage());
        }
    }

    private String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser) {
            return ((CurrentUser) auth.getPrincipal()).getUsername();
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
