package com.example.cube.admin.service;

import com.example.cube.admin.dto.GrantRequest;
import com.example.cube.admin.dto.GrantResponse;
import com.example.cube.admin.dto.GroupRequest;
import com.example.cube.admin.dto.GroupResponse;
import com.example.cube.audit.AuditService;
import com.example.cube.auth.Permissions;
import com.example.cube.auth.entity.Access;
import com.example.cube.auth.entity.Group;
import com.example.cube.auth.entity.GroupAccessTemplate;
import com.example.cube.auth.entity.GroupAccessTemplateId;
import com.example.cube.auth.repository.GroupAccessTemplateRepository;
import com.example.cube.auth.repository.GroupRepository;
import com.example.cube.auth.repository.UserGroupRepository;
import com.example.cube.common.RecordStatus;
import com.example.cube.common.exception.ApiException;
import com.example.cube.security.SecurityUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional
public class AdminGroupService {

    private final GroupRepository groupRepository;
    private final UserGroupRepository userGroupRepository;
    private final GroupAccessTemplateRepository grantRepository;
    private final AdminCatalogService catalogService;
    private final AuditService auditService;

    public AdminGroupService(GroupRepository groupRepository, UserGroupRepository userGroupRepository,
                             GroupAccessTemplateRepository grantRepository, AdminCatalogService catalogService,
                             AuditService auditService) {
        this.groupRepository = groupRepository;
        this.userGroupRepository = userGroupRepository;
        this.grantRepository = grantRepository;
        this.catalogService = catalogService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> list() {
        SecurityUtils.requireAny(Permissions.ANY_ADMIN);
        List<GroupResponse> result = new ArrayList<>();
        for (Group g : groupRepository.findAll(Sort.by("groupCode"))) {
            result.add(toResponse(g));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public GroupResponse get(Long id) {
        SecurityUtils.requireAny(Permissions.ANY_ADMIN);
        return toResponse(load(id));
    }

    public GroupResponse create(GroupRequest request) {
        SecurityUtils.require(Permissions.MANAGE_GROUPS);
        String code = AdminCatalogService.normalizeCode(request.getGroupCode());
        if (groupRepository.existsByGroupCodeIgnoreCase(code)) {
            throw ApiException.conflict("Group code '" + code + "' already exists");
        }
        Group g = new Group();
        g.setGroupCode(code);
        g.setGroupName(AdminCatalogService.requireName(request.getGroupName()));
        g.setDescription(AdminCatalogService.trimToNull(request.getDescription()));
        g.setStatus(request.getStatus() == null ? RecordStatus.ACTIVE : request.getStatus());
        g.setCreatedAt(LocalDateTime.now());
        g = groupRepository.save(g);
        auditService.record("GROUP_CREATE", "GROUP", g.getGroupId(), code);
        return toResponse(g);
    }

    public GroupResponse update(Long id, GroupRequest request) {
        SecurityUtils.require(Permissions.MANAGE_GROUPS);
        Group g = load(id);
        if (request.getGroupName() != null) {
            g.setGroupName(AdminCatalogService.requireName(request.getGroupName()));
        }
        if (request.getDescription() != null) {
            g.setDescription(AdminCatalogService.trimToNull(request.getDescription()));
        }
        if (request.getStatus() != null) {
            g.setStatus(request.getStatus());
        }
        auditService.record("GROUP_UPDATE", "GROUP", id, "status=" + g.getStatus());
        return toResponse(g);
    }

    public void delete(Long id) {
        SecurityUtils.require(Permissions.MANAGE_GROUPS);
        Group g = load(id);
        long members = userGroupRepository.countByGroupId(id);
        if (members > 0) {
            throw ApiException.conflict("Group has " + members + " member(s). Move them to another group first.");
        }
        grantRepository.deleteAll(grantRepository.findByIdGroupId(id));
        groupRepository.delete(g);
        auditService.record("GROUP_DELETE", "GROUP", id, g.getGroupCode());
    }

    // ------------------------------------------------------------------ grants

    @Transactional(readOnly = true)
    public List<GrantResponse> listGrants(Long groupId) {
        SecurityUtils.requireAny(Permissions.ANY_ADMIN);
        load(groupId);
        Map<Long, String[]> codes = catalogService.accessCodes();
        List<GrantResponse> result = new ArrayList<>();
        for (GroupAccessTemplate t : grantRepository.findByIdGroupId(groupId)) {
            String[] code = codes.get(t.getId().getAccessId());
            GrantResponse r = new GrantResponse();
            if (code != null) {
                r.setModuleCode(code[0]);
                r.setAccessCode(code[1]);
                r.setPermission(Permissions.code(code[0], code[1]));
            }
            r.setAllowed(t.isAllowed());
            r.setStatus(t.getStatus() == null ? RecordStatus.ACTIVE : t.getStatus());
            result.add(r);
        }
        return result;
    }

    /** Creates or replaces the grant for (group, access). */
    public List<GrantResponse> upsertGrant(Long groupId, GrantRequest request) {
        SecurityUtils.require(Permissions.MANAGE_GROUP_ACCESS);
        load(groupId);
        Access access = catalogService.requireAccess(request.getModuleCode(), request.getAccessCode());
        GroupAccessTemplateId id = new GroupAccessTemplateId(groupId, access.getAccessId());

        GroupAccessTemplate grant = grantRepository.findById(id).orElse(null);
        if (grant == null) {
            grant = new GroupAccessTemplate();
            grant.setId(id);
            grant.setCreatedAt(LocalDateTime.now());
        }
        grant.setAllowed(Boolean.TRUE.equals(request.getAllowed()) ? RecordStatus.YES : RecordStatus.NO);
        grant.setStatus(RecordStatus.ACTIVE);
        grantRepository.save(grant);
        auditService.record("GROUP_GRANT", "GROUP", groupId,
                Permissions.code(request.getModuleCode().toUpperCase(Locale.ROOT), access.getAccessCode()) + "=" + grant.getAllowed());
        return listGrants(groupId);
    }

    public void deleteGrant(Long groupId, String moduleCode, String accessCode) {
        SecurityUtils.require(Permissions.MANAGE_GROUP_ACCESS);
        load(groupId);
        Access access = catalogService.requireAccess(moduleCode, accessCode);
        GroupAccessTemplate grant = grantRepository.findById(new GroupAccessTemplateId(groupId, access.getAccessId()))
                .orElseThrow(() -> ApiException.notFound("Grant not found"));
        grantRepository.delete(grant);
        auditService.record("GROUP_GRANT_DELETE", "GROUP", groupId,
                Permissions.code(moduleCode.toUpperCase(Locale.ROOT), access.getAccessCode()));
    }

    // ------------------------------------------------------------------ helpers

    public Group requireActiveGroupByCode(String code) {
        String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        Group g = groupRepository.findByGroupCode(c)
                .orElseThrow(() -> ApiException.badRequest("Unknown group code '" + code + "'"));
        if (!g.isActive()) {
            throw ApiException.badRequest("Group '" + c + "' is inactive");
        }
        return g;
    }

    private Group load(Long id) {
        return groupRepository.findById(id).orElseThrow(() -> ApiException.notFound("Group not found"));
    }

    private GroupResponse toResponse(Group g) {
        GroupResponse r = new GroupResponse();
        r.setGroupId(g.getGroupId());
        r.setGroupCode(g.getGroupCode());
        r.setGroupName(g.getGroupName());
        r.setDescription(g.getDescription());
        r.setStatus(g.getStatus() == null ? RecordStatus.ACTIVE : g.getStatus());
        r.setMemberCount(userGroupRepository.countByGroupId(g.getGroupId()));
        r.setCreatedAt(g.getCreatedAt());
        return r;
    }
}
