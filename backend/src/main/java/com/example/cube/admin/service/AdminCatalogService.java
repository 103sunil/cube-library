package com.example.cube.admin.service;

import com.example.cube.admin.dto.CatalogItemRequest;
import com.example.cube.admin.dto.CatalogItemResponse;
import com.example.cube.audit.AuditService;
import com.example.cube.auth.Permissions;
import com.example.cube.auth.entity.Access;
import com.example.cube.auth.entity.Module;
import com.example.cube.auth.repository.AccessRepository;
import com.example.cube.auth.repository.GroupAccessTemplateRepository;
import com.example.cube.auth.repository.ModuleRepository;
import com.example.cube.auth.repository.UserAccessRepository;
import com.example.cube.common.RecordStatus;
import com.example.cube.common.exception.ApiException;
import com.example.cube.security.SecurityUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Modules and their access codes. Codes the application checks cannot be deleted (deactivate instead).
 */
@Service
@Transactional
public class AdminCatalogService {

    private static final Set<String> SYSTEM_MODULES = new HashSet<>(Arrays.asList(
            Permissions.MODULE_KNOWLEDGE, Permissions.MODULE_AUTH));
    private static final Set<String> SYSTEM_PERMISSIONS = new HashSet<>(Arrays.asList(
            Permissions.KNOWLEDGE_VIEW, Permissions.KNOWLEDGE_SEARCH, Permissions.KNOWLEDGE_DOWNLOAD,
            Permissions.KNOWLEDGE_SUBMIT, Permissions.KNOWLEDGE_APPROVE, Permissions.KNOWLEDGE_EDIT,
            Permissions.KNOWLEDGE_DELETE, Permissions.MANAGE_USERS, Permissions.MANAGE_GROUPS,
            Permissions.MANAGE_MODULES, Permissions.MANAGE_ACCESS, Permissions.MANAGE_GROUP_ACCESS,
            Permissions.MANAGE_USER_ACCESS));

    private final ModuleRepository moduleRepository;
    private final AccessRepository accessRepository;
    private final GroupAccessTemplateRepository groupAccessRepository;
    private final UserAccessRepository userAccessRepository;
    private final AuditService auditService;

    public AdminCatalogService(ModuleRepository moduleRepository, AccessRepository accessRepository,
                               GroupAccessTemplateRepository groupAccessRepository,
                               UserAccessRepository userAccessRepository, AuditService auditService) {
        this.moduleRepository = moduleRepository;
        this.accessRepository = accessRepository;
        this.groupAccessRepository = groupAccessRepository;
        this.userAccessRepository = userAccessRepository;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ modules

    @Transactional(readOnly = true)
    public List<CatalogItemResponse> listModules() {
        SecurityUtils.requireAny(Permissions.ANY_ADMIN);
        List<CatalogItemResponse> result = new ArrayList<>();
        for (Module m : moduleRepository.findAll(Sort.by("moduleCode"))) {
            result.add(toResponse(m));
        }
        return result;
    }

    public CatalogItemResponse createModule(CatalogItemRequest request) {
        SecurityUtils.require(Permissions.MANAGE_MODULES);
        String code = normalizeCode(request.getCode());
        if (moduleRepository.existsByModuleCodeIgnoreCase(code)) {
            throw ApiException.conflict("Module code '" + code + "' already exists");
        }
        Module m = new Module();
        m.setModuleCode(code);
        m.setModuleName(requireName(request.getName()));
        m.setDescription(trimToNull(request.getDescription()));
        m.setStatus(request.getStatus() == null ? RecordStatus.ACTIVE : request.getStatus());
        m.setCreatedAt(LocalDateTime.now());
        m = moduleRepository.save(m);
        auditService.record("MODULE_CREATE", "MODULE", m.getModuleId(), code);
        return toResponse(m);
    }

    public CatalogItemResponse updateModule(Long id, CatalogItemRequest request) {
        SecurityUtils.require(Permissions.MANAGE_MODULES);
        Module m = moduleRepository.findById(id).orElseThrow(() -> ApiException.notFound("Module not found"));
        if (request.getName() != null) {
            m.setModuleName(requireName(request.getName()));
        }
        if (request.getDescription() != null) {
            m.setDescription(trimToNull(request.getDescription()));
        }
        if (request.getStatus() != null) {
            m.setStatus(request.getStatus());
        }
        auditService.record("MODULE_UPDATE", "MODULE", id, "status=" + m.getStatus());
        return toResponse(m);
    }

    public void deleteModule(Long id) {
        SecurityUtils.require(Permissions.MANAGE_MODULES);
        Module m = moduleRepository.findById(id).orElseThrow(() -> ApiException.notFound("Module not found"));
        if (SYSTEM_MODULES.contains(m.getModuleCode())) {
            throw ApiException.conflict("Module " + m.getModuleCode() + " is used by the application and cannot be deleted. Deactivate it instead.");
        }
        if (accessRepository.existsByModuleId(id)) {
            throw ApiException.conflict("Module still has access codes. Delete those first or deactivate the module.");
        }
        moduleRepository.delete(m);
        auditService.record("MODULE_DELETE", "MODULE", id, m.getModuleCode());
    }

    // ------------------------------------------------------------------ access codes

    @Transactional(readOnly = true)
    public List<CatalogItemResponse> listAccessCodes() {
        SecurityUtils.requireAny(Permissions.ANY_ADMIN);
        Map<Long, String> modules = moduleCodes();
        List<CatalogItemResponse> result = new ArrayList<>();
        for (Access a : accessRepository.findAll(Sort.by("moduleId", "accessId"))) {
            result.add(toResponse(a, modules.get(a.getModuleId())));
        }
        return result;
    }

    public CatalogItemResponse createAccess(CatalogItemRequest request) {
        SecurityUtils.require(Permissions.MANAGE_ACCESS);
        Module module = requireModule(request.getModuleCode());
        String code = normalizeCode(request.getCode());
        if (accessRepository.existsByModuleIdAndAccessCodeIgnoreCase(module.getModuleId(), code)) {
            throw ApiException.conflict("Access code '" + code + "' already exists in module " + module.getModuleCode());
        }
        Access a = new Access();
        a.setModuleId(module.getModuleId());
        a.setAccessCode(code);
        a.setDescription(trimToNull(request.getDescription() != null ? request.getDescription() : request.getName()));
        a.setStatus(request.getStatus() == null ? RecordStatus.ACTIVE : request.getStatus());
        a.setCreatedAt(LocalDateTime.now());
        a = accessRepository.save(a);
        auditService.record("ACCESS_CREATE", "ACCESS", a.getAccessId(), Permissions.code(module.getModuleCode(), code));
        return toResponse(a, module.getModuleCode());
    }

    public CatalogItemResponse updateAccess(Long id, CatalogItemRequest request) {
        SecurityUtils.require(Permissions.MANAGE_ACCESS);
        Access a = accessRepository.findById(id).orElseThrow(() -> ApiException.notFound("Access code not found"));
        String text = request.getDescription() != null ? request.getDescription() : request.getName();
        if (text != null) {
            a.setDescription(trimToNull(text));
        }
        if (request.getStatus() != null) {
            a.setStatus(request.getStatus());
        }
        auditService.record("ACCESS_UPDATE", "ACCESS", id, "status=" + a.getStatus());
        return toResponse(a, moduleCodes().get(a.getModuleId()));
    }

    public void deleteAccess(Long id) {
        SecurityUtils.require(Permissions.MANAGE_ACCESS);
        Access a = accessRepository.findById(id).orElseThrow(() -> ApiException.notFound("Access code not found"));
        String permission = Permissions.code(String.valueOf(moduleCodes().get(a.getModuleId())), a.getAccessCode());
        if (SYSTEM_PERMISSIONS.contains(permission)) {
            throw ApiException.conflict(permission + " is used by the application and cannot be deleted. Deactivate it instead.");
        }
        if (groupAccessRepository.existsByIdAccessId(id) || userAccessRepository.existsByIdAccessId(id)) {
            throw ApiException.conflict("Access code is used by group grants or user overrides. Remove those first or deactivate it.");
        }
        accessRepository.delete(a);
        auditService.record("ACCESS_DELETE", "ACCESS", id, permission);
    }

    // ------------------------------------------------------------------ shared lookups

    @Transactional(readOnly = true)
    public Module requireModule(String code) {
        String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        return moduleRepository.findByModuleCode(c)
                .orElseThrow(() -> ApiException.badRequest("Unknown module code '" + code + "'"));
    }

    /** Resolves MODULE + ACCESS codes to the access row. */
    @Transactional(readOnly = true)
    public Access requireAccess(String moduleCode, String accessCode) {
        Module module = requireModule(moduleCode);
        String c = accessCode == null ? "" : accessCode.trim().toUpperCase(Locale.ROOT);
        return accessRepository.findByModuleIdAndAccessCode(module.getModuleId(), c)
                .orElseThrow(() -> ApiException.badRequest("Unknown access code '" + accessCode + "' in module " + module.getModuleCode()));
    }

    @Transactional(readOnly = true)
    public Map<Long, String> moduleCodes() {
        Map<Long, String> map = new HashMap<>();
        for (Module m : moduleRepository.findAll()) {
            map.put(m.getModuleId(), m.getModuleCode());
        }
        return map;
    }

    /** accessId -> {moduleCode, accessCode} */
    @Transactional(readOnly = true)
    public Map<Long, String[]> accessCodes() {
        Map<Long, String> modules = moduleCodes();
        Map<Long, String[]> map = new HashMap<>();
        for (Access a : accessRepository.findAll()) {
            map.put(a.getAccessId(), new String[]{modules.get(a.getModuleId()), a.getAccessCode()});
        }
        return map;
    }

    static String normalizeCode(String code) {
        String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!c.matches("[A-Z0-9_]{1,50}")) {
            throw ApiException.badRequest("Code is required: 1-50 characters, letters, digits and '_' only");
        }
        return c;
    }

    static String requireName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            throw ApiException.badRequest("Name is required");
        }
        return n;
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    private static CatalogItemResponse toResponse(Module m) {
        CatalogItemResponse r = new CatalogItemResponse();
        r.setId(m.getModuleId());
        r.setCode(m.getModuleCode());
        r.setName(m.getModuleName());
        r.setDescription(m.getDescription());
        r.setStatus(m.getStatus() == null ? RecordStatus.ACTIVE : m.getStatus());
        r.setCreatedAt(m.getCreatedAt());
        return r;
    }

    private static CatalogItemResponse toResponse(Access a, String moduleCode) {
        CatalogItemResponse r = new CatalogItemResponse();
        r.setId(a.getAccessId());
        r.setCode(a.getAccessCode());
        r.setModuleCode(moduleCode);
        r.setPermission(moduleCode == null ? null : Permissions.code(moduleCode, a.getAccessCode()));
        r.setName(a.getDescription());
        r.setDescription(a.getDescription());
        r.setStatus(a.getStatus() == null ? RecordStatus.ACTIVE : a.getStatus());
        r.setCreatedAt(a.getCreatedAt());
        return r;
    }
}
