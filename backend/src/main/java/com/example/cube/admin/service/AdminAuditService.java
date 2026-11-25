package com.example.cube.admin.service;

import com.example.cube.admin.dto.AuditResponse;
import com.example.cube.audit.AuditLog;
import com.example.cube.audit.AuditLogRepository;
import com.example.cube.auth.Permissions;
import com.example.cube.common.response.PageResponse;
import com.example.cube.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdminAuditService {

    private final AuditLogRepository repository;

    public AdminAuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public PageResponse<AuditResponse> list(String actor, Integer page, Integer size) {
        SecurityUtils.require(Permissions.MANAGE_USERS);
        Pageable pageable = PageResponse.pageable(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("auditId")));
        Page<AuditLog> result = actor == null || actor.trim().isEmpty()
                ? repository.findAll(pageable)
                : repository.findByActorIgnoreCase(actor.trim(), pageable);
        List<AuditResponse> content = new ArrayList<>();
        for (AuditLog a : result.getContent()) {
            AuditResponse r = new AuditResponse();
            r.setAuditId(a.getAuditId());
            r.setActor(a.getActor());
            r.setAction(a.getAction());
            r.setEntityType(a.getEntityType());
            r.setEntityId(a.getEntityId());
            r.setDetails(a.getDetails());
            r.setCreatedAt(a.getCreatedAt());
            content.add(r);
        }
        return PageResponse.of(result, content);
    }
}
