package com.example.cube.admin.service;

import com.example.cube.admin.dto.CreateUserRequest;
import com.example.cube.admin.dto.GrantRequest;
import com.example.cube.admin.dto.GrantResponse;
import com.example.cube.admin.dto.UpdateUserRequest;
import com.example.cube.admin.dto.UserOption;
import com.example.cube.admin.dto.UserResponse;
import com.example.cube.knowledge.repository.KnowledgeRepository;
import com.example.cube.knowledge.service.ReviewRouting;
import com.example.cube.audit.AuditService;
import com.example.cube.auth.Levels;
import com.example.cube.auth.Permissions;
import com.example.cube.auth.entity.Access;
import com.example.cube.auth.entity.Group;
import com.example.cube.auth.entity.Module;
import com.example.cube.auth.entity.User;
import com.example.cube.auth.entity.UserAccess;
import com.example.cube.auth.entity.UserAccessId;
import com.example.cube.auth.entity.UserGroup;
import com.example.cube.auth.repository.AccessRepository;
import com.example.cube.auth.repository.GroupRepository;
import com.example.cube.auth.repository.ModuleRepository;
import com.example.cube.auth.repository.UserAccessRepository;
import com.example.cube.auth.repository.UserGroupRepository;
import com.example.cube.auth.repository.UserRepository;
import com.example.cube.auth.service.PermissionService;
import com.example.cube.common.RecordStatus;
import com.example.cube.common.exception.ApiException;
import com.example.cube.common.response.PageResponse;
import com.example.cube.security.CurrentUser;
import com.example.cube.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserGroupRepository userGroupRepository;
    private final GroupRepository groupRepository;
    private final UserAccessRepository userAccessRepository;
    private final ModuleRepository moduleRepository;
    private final AccessRepository accessRepository;
    private final AdminGroupService groupService;
    private final AdminCatalogService catalogService;
    private final PermissionService permissionService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final ReviewRouting routing;
    private final KnowledgeRepository knowledgeRepository;

    public AdminUserService(UserRepository userRepository, UserGroupRepository userGroupRepository,
                            GroupRepository groupRepository, UserAccessRepository userAccessRepository,
                            ModuleRepository moduleRepository, AccessRepository accessRepository,
                            AdminGroupService groupService, AdminCatalogService catalogService,
                            PermissionService permissionService, PasswordEncoder passwordEncoder,
                            AuditService auditService, ReviewRouting routing,
                            KnowledgeRepository knowledgeRepository) {
        this.routing = routing;
        this.knowledgeRepository = knowledgeRepository;
        this.userRepository = userRepository;
        this.userGroupRepository = userGroupRepository;
        this.groupRepository = groupRepository;
        this.userAccessRepository = userAccessRepository;
        this.moduleRepository = moduleRepository;
        this.accessRepository = accessRepository;
        this.groupService = groupService;
        this.catalogService = catalogService;
        this.permissionService = permissionService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ users

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String query, Integer page, Integer size) {
        return list(query, false, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String query, boolean removed, Integer page, Integer size) {
        SecurityUtils.require(Permissions.MANAGE_USERS);
        Pageable pageable = PageResponse.pageable(page, size, Sort.by("username"));
        String pattern = query == null || query.trim().isEmpty()
                ? "%" : "%" + query.trim().toLowerCase(java.util.Locale.ROOT) + "%";
        Page<User> result = userRepository.search(removed ? "Y" : "N", pattern, pageable);

        List<Long> ids = new ArrayList<>();
        for (User u : result.getContent()) {
            ids.add(u.getUserId());
        }
        Map<Long, UserGroup> memberships = new HashMap<>();
        for (UserGroup ug : userGroupRepository.findAllById(ids)) {
            memberships.put(ug.getUserId(), ug);
        }
        Map<Long, Group> groups = new HashMap<>();
        for (Group g : groupRepository.findAll()) {
            groups.put(g.getGroupId(), g);
        }
        Set<Long> managerIds = new HashSet<>();
        for (User u : result.getContent()) {
            if (u.getReportingManagerId() != null) {
                managerIds.add(u.getReportingManagerId());
            }
        }
        Map<Long, User> managers = new HashMap<>();
        for (User m : userRepository.findAllById(managerIds)) {
            managers.put(m.getUserId(), m);
        }
        List<UserResponse> content = new ArrayList<>();
        for (User u : result.getContent()) {
            UserGroup ug = memberships.get(u.getUserId());
            content.add(toResponse(u, ug == null ? null : groups.get(ug.getGroupId()), managers.get(u.getReportingManagerId())));
        }
        return PageResponse.of(result, content);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        SecurityUtils.require(Permissions.MANAGE_USERS);
        return toResponse(load(id));
    }

    public UserResponse create(CreateUserRequest request) {
        SecurityUtils.require(Permissions.MANAGE_USERS);
        if (request.getGroupCode() == null || request.getGroupCode().trim().isEmpty()) {
            throw ApiException.badRequest("Group is required");
        }
        return createUnchecked(request);
    }

    /** Creates a user. The caller has already checked permissions (admin or team manager). */
    public UserResponse createUnchecked(CreateUserRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("Username '" + username + "' already exists");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Email '" + email + "' is already used by another user");
        }
        Group group = groupService.requireActiveGroupByCode(request.getGroupCode());

        LocalDateTime now = LocalDateTime.now();
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        u.setFirstName(AdminCatalogService.trimToNull(request.getFirstName()));
        u.setLastName(AdminCatalogService.trimToNull(request.getLastName()));
        u.setStatus(request.getStatus() == null ? RecordStatus.ACTIVE : request.getStatus());
        u.setCreatedAt(now);
        u.setUpdatedAt(now);
        String employeeCode = AdminCatalogService.trimToNull(request.getEmployeeCode());
        if (employeeCode != null && userRepository.existsByEmployeeCodeIgnoreCase(employeeCode)) {
            throw ApiException.conflict("Employee ID '" + employeeCode + "' is already used by another user");
        }
        u.setEmployeeCode(employeeCode);
        u.setDesignation(AdminCatalogService.trimToNull(request.getDesignation()));
        // The creator sets a temporary password; the user must choose their own at first login.
        u.setMustChangePassword("Y");
        if (request.getReportingManagerUsername() != null && !request.getReportingManagerUsername().trim().isEmpty()) {
            u.setReportingManagerId(resolveManager(request.getReportingManagerUsername(), null, username,
                    Levels.of(u.getDesignation(), group.getGroupCode())).getUserId());
        }
        u = userRepository.save(u);
        assignGroup(u.getUserId(), group.getGroupId());

        auditService.record("USER_CREATE", "USER", u.getUserId(), username + " group=" + group.getGroupCode());
        return toResponse(u);
    }

    public UserResponse update(Long id, UpdateUserRequest request) {
        CurrentUser me = SecurityUtils.require(Permissions.MANAGE_USERS);
        return updateUnchecked(id, request, me);
    }

    /** Updates a user. The caller has already checked permissions (admin or team manager). */
    public UserResponse updateUnchecked(Long id, UpdateUserRequest request, CurrentUser me) {
        User u = load(id);
        boolean self = u.getUserId().equals(me.getUserId());

        if (request.getEmail() != null) {
            String email = request.getEmail().trim();
            if (userRepository.existsByEmailIgnoreCaseAndUserIdNot(email, id)) {
                throw ApiException.conflict("Email '" + email + "' is already used by another user");
            }
            u.setEmail(email);
        }
        if (request.getFirstName() != null) {
            u.setFirstName(AdminCatalogService.trimToNull(request.getFirstName()));
        }
        if (request.getLastName() != null) {
            u.setLastName(AdminCatalogService.trimToNull(request.getLastName()));
        }
        if (request.getStatus() != null && !request.getStatus().equals(u.getStatus())) {
            if (self && RecordStatus.INACTIVE.equals(request.getStatus())) {
                throw ApiException.badRequest("You cannot deactivate your own account");
            }
            u.setStatus(request.getStatus());
        }
        if (request.getGroupCode() != null) {
            Group group = groupService.requireActiveGroupByCode(request.getGroupCode());
            if (self) {
                Set<String> current = permissionService.effectivePermissions(id);
                if (current.contains(Permissions.MANAGE_USERS)
                        && !findGroupId(id).equals(group.getGroupId())) {
                    throw ApiException.badRequest("You cannot change your own group. Ask another administrator.");
                }
            }
            assignGroup(id, group.getGroupId());
        }
        if (request.getEmployeeCode() != null) {
            String employeeCode = AdminCatalogService.trimToNull(request.getEmployeeCode());
            if (employeeCode != null && userRepository.existsByEmployeeCodeIgnoreCaseAndUserIdNot(employeeCode, id)) {
                throw ApiException.conflict("Employee ID '" + employeeCode + "' is already used by another user");
            }
            u.setEmployeeCode(employeeCode);
        }
        if (request.getDesignation() != null) {
            u.setDesignation(AdminCatalogService.trimToNull(request.getDesignation()));
        }
        userRepository.flush();
        int myLevel = levelOf(u);
        // A new title must still fit under the current manager (unless a new manager is being chosen)...
        if (request.getReportingManagerUsername() == null && u.getReportingManagerId() != null) {
            userRepository.findById(u.getReportingManagerId()).ifPresent(m -> {
                String problem = Levels.checkReportsTo(u.getUsername(), myLevel, m.getUsername(), levelOf(m));
                if (problem != null) {
                    throw ApiException.badRequest(problem + ". Change 'Reports to' as well.");
                }
            });
        }
        // ...and above the people who report to this user.
        for (User report : userRepository.findByReportingManagerIdOrderByUsernameAsc(id)) {
            String problem = Levels.checkReportsTo(report.getUsername(), levelOf(report), u.getUsername(), myLevel);
            if (problem != null) {
                throw ApiException.badRequest(problem + ". Move the people who report to " + u.getUsername() + " first.");
            }
        }
        String managerChange = "";
        if (request.getReportingManagerUsername() != null) {
            String requested = request.getReportingManagerUsername().trim();
            Long newManager = requested.isEmpty() ? null : resolveManager(requested, id, u.getUsername(), myLevel).getUserId();
            boolean changed = newManager == null ? u.getReportingManagerId() != null : !newManager.equals(u.getReportingManagerId());
            if (changed) {
                u.setReportingManagerId(newManager);
                // Waiting submissions (theirs and their team's) follow to the new approver.
                int moved = reroute(id);
                managerChange = ", reportingManager=" + (requested.isEmpty() ? "none" : requested) + ", pendingMoved=" + moved;
            }
        }
        u.setUpdatedAt(LocalDateTime.now());
        auditService.record("USER_UPDATE", "USER", id, "status=" + u.getStatus()
                + (request.getGroupCode() != null ? ", group=" + request.getGroupCode() : "") + managerChange);
        return toResponse(u);
    }

    // ------------------------------------------------------------------ remove / restore

    public Map<String, Object> remove(Long id) {
        CurrentUser me = SecurityUtils.require(Permissions.MANAGE_USERS);
        return removeUnchecked(id, me);
    }

    /**
     * Removes a user who left (soft delete, status D): they can no longer sign in and disappear from lists,
     * but their published knowledge stays. People who reported to them move up to their manager
     * (or to "no one" if they were at the top), together with any submissions waiting for review.
     */
    public Map<String, Object> removeUnchecked(Long id, CurrentUser me) {
        User u = load(id);
        if (u.getUserId().equals(me.getUserId())) {
            throw ApiException.badRequest("You cannot delete your own account");
        }
        if (RecordStatus.REMOVED.equals(u.getStatus())) {
            throw ApiException.notFound("User not found");
        }
        Set<Long> admins = permissionService.usersWithPermission(Permissions.MANAGE_USERS);
        if (admins.contains(id) && admins.size() <= 1) {
            throw ApiException.badRequest("This is the only administrator. Create another administrator first.");
        }
        Long newManager = u.getReportingManagerId();
        int movedPeople = 0;
        List<User> reports = userRepository.findByReportingManagerIdOrderByUsernameAsc(id);
        for (User report : reports) {
            report.setReportingManagerId(newManager);
            report.setUpdatedAt(LocalDateTime.now());
            movedPeople++;
        }
        u.setStatus(RecordStatus.REMOVED);
        u.setReportingManagerId(null);
        userRepository.flush();
        for (User report : reports) {
            reroute(report.getUserId());
        }
        knowledgeRepository.moveReviewer(id, newManager == null ? null : routing.firstApproverFrom(newManager));

        u.setStatus(RecordStatus.REMOVED);
        u.setReportingManagerId(null);
        u.setUpdatedAt(LocalDateTime.now());
        User manager = newManager == null ? null : userRepository.findById(newManager).orElse(null);
        auditService.record("USER_REMOVE", "USER", id, "username=" + u.getUsername() + ", reportsMovedTo="
                + (manager == null ? "none" : manager.getUsername()) + ", reportsMoved=" + movedPeople);

        Map<String, Object> result = new HashMap<>();
        result.put("username", u.getUsername());
        result.put("reportsMoved", movedPeople);
        result.put("reportsMovedTo", manager == null ? null : manager.getUsername());
        return result;
    }

    /** Re-assigns waiting submissions of this person and everyone below them to their current approver. */
    private int reroute(Long userId) {
        userRepository.flush();
        int moved = knowledgeRepository.reassignPending(userId, routing.reviewerFor(userId));
        for (User below : routing.everyoneBelow(userId)) {
            moved += knowledgeRepository.reassignPending(below.getUserId(), routing.reviewerFor(below.getUserId()));
        }
        return moved;
    }

    /** Active users at Team Lead level or higher: who anyone can report to (filtered further by level in the UI). */
    @Transactional(readOnly = true)
    public List<UserOption> reportingOptions() {
        SecurityUtils.require(Permissions.MANAGE_USERS);
        List<UserOption> result = new ArrayList<>();
        for (User u : userRepository.findAll(Sort.by("username"))) {
            UserOption o = option(u);
            if (u.isActive() && o.getLevel() >= Levels.TEAM_LEAD) {
                result.add(o);
            }
        }
        result.sort((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()));
        return result;
    }

    public UserOption option(User u) {
        UserOption o = new UserOption(u.getUserId(), u.getUsername(), u.getDisplayName());
        o.setDesignation(u.getDesignation());
        o.setLevel(levelOf(u));
        return o;
    }

    /** Brings back a removed user (inactive admin action). They start with no reporting manager. */
    public UserResponse restore(Long id) {
        SecurityUtils.require(Permissions.MANAGE_USERS);
        User u = load(id);
        if (!RecordStatus.REMOVED.equals(u.getStatus())) {
            throw ApiException.badRequest("This user is not deleted");
        }
        u.setStatus(RecordStatus.ACTIVE);
        u.setUpdatedAt(LocalDateTime.now());
        auditService.record("USER_RESTORE", "USER", id, "username=" + u.getUsername());
        return toResponse(u);
    }

    public void resetPassword(Long id, String password) {
        SecurityUtils.require(Permissions.MANAGE_USERS);
        resetPasswordUnchecked(id, password);
    }

    public void resetPasswordUnchecked(Long id, String password) {
        User u = load(id);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setMustChangePassword("Y");
        u.setUpdatedAt(LocalDateTime.now());
        auditService.record("USER_PASSWORD_RESET", "USER", id, null);
    }

    @Transactional(readOnly = true)
    public List<String> effectivePermissions(Long id) {
        SecurityUtils.requireAny(Permissions.MANAGE_USERS, Permissions.MANAGE_USER_ACCESS);
        load(id);
        return new ArrayList<>(permissionService.effectivePermissions(id));
    }

    // ------------------------------------------------------------------ overrides

    @Transactional(readOnly = true)
    public List<GrantResponse> listOverrides(Long userId) {
        SecurityUtils.require(Permissions.MANAGE_USER_ACCESS);
        load(userId);
        Map<Long, String[]> codes = catalogService.accessCodes();
        LocalDate today = LocalDate.now();
        List<GrantResponse> result = new ArrayList<>();
        for (UserAccess o : userAccessRepository.findByIdUserId(userId)) {
            String[] code = codes.get(o.getId().getAccessId());
            GrantResponse r = new GrantResponse();
            if (code != null) {
                r.setModuleCode(code[0]);
                r.setAccessCode(code[1]);
                r.setPermission(Permissions.code(code[0], code[1]));
            }
            r.setAllowed(o.isAllowed());
            r.setStatus(o.getStatus() == null ? RecordStatus.ACTIVE : o.getStatus());
            r.setExpiryDate(o.getExpiryDate());
            r.setEffective(o.isEffectiveOn(today));
            result.add(r);
        }
        return result;
    }

    /** Creates or replaces an override. allowed=false explicitly removes a permission the group grants. */
    public List<GrantResponse> upsertOverride(Long userId, GrantRequest request) {
        SecurityUtils.require(Permissions.MANAGE_USER_ACCESS);
        load(userId);
        if (request.getExpiryDate() != null && request.getExpiryDate().isBefore(LocalDate.now())) {
            throw ApiException.badRequest("expiryDate cannot be in the past");
        }
        Module module = catalogService.requireModule(request.getModuleCode());
        Access access = catalogService.requireAccess(request.getModuleCode(), request.getAccessCode());
        UserAccessId id = new UserAccessId(userId, access.getAccessId());

        UserAccess o = userAccessRepository.findById(id).orElse(null);
        if (o == null) {
            o = new UserAccess();
            o.setId(id);
            o.setCreatedAt(LocalDateTime.now());
        }
        o.setAllowed(Boolean.TRUE.equals(request.getAllowed()) ? RecordStatus.YES : RecordStatus.NO);
        o.setExpiryDate(request.getExpiryDate());
        o.setStatus(RecordStatus.ACTIVE);
        userAccessRepository.save(o);
        auditService.record("USER_OVERRIDE", "USER", userId,
                Permissions.code(module.getModuleCode(), access.getAccessCode()) + "=" + o.getAllowed()
                        + (o.getExpiryDate() != null ? " until " + o.getExpiryDate() : ""));
        return listOverrides(userId);
    }

    public void deleteOverride(Long userId, String moduleCode, String accessCode) {
        SecurityUtils.require(Permissions.MANAGE_USER_ACCESS);
        load(userId);
        Module module = catalogService.requireModule(moduleCode);
        Access access = catalogService.requireAccess(moduleCode, accessCode);
        UserAccessId id = new UserAccessId(userId, access.getAccessId());
        UserAccess o = userAccessRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Override not found"));
        userAccessRepository.delete(o);
        auditService.record("USER_OVERRIDE_DELETE", "USER", userId,
                Permissions.code(module.getModuleCode(), access.getAccessCode()));
    }

    // ------------------------------------------------------------------ helpers

    private User load(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private Long findGroupId(Long userId) {
        UserGroup ug = userGroupRepository.findById(userId).orElse(null);
        return ug == null ? Long.valueOf(-1L) : ug.getGroupId();
    }

    private void assignGroup(Long userId, Long groupId) {
        UserGroup ug = userGroupRepository.findById(userId).orElse(null);
        if (ug == null) {
            ug = new UserGroup();
            ug.setUserId(userId);
            ug.setCreatedAt(LocalDateTime.now());
        }
        ug.setGroupId(groupId);
        ug.setStatus(RecordStatus.ACTIVE);
        userGroupRepository.save(ug);
    }

    /**
     * Checks a reporting manager: must exist, be active, not be the user themself, and be able to approve.
     */
    private User resolveManager(String username, Long forUserId, String personName, int personLevel) {
        User manager = resolveManager(username, forUserId);
        String problem = Levels.checkReportsTo(personName, personLevel, manager.getUsername(), levelOf(manager));
        if (problem != null) {
            throw ApiException.badRequest(problem);
        }
        return manager;
    }

    /** The user's place on the reporting ladder (see {@link Levels}). */
    public int levelOf(User u) {
        return Levels.of(u.getDesignation(), groupCodeOf(u.getUserId()));
    }

    private String groupCodeOf(Long userId) {
        Long groupId = findGroupId(userId);
        return groupRepository.findById(groupId).map(Group::getGroupCode).orElse(null);
    }

    private User resolveManager(String username, Long forUserId) {
        String name = username.trim();
        User manager = userRepository.findByUsername(name)
                .orElseThrow(() -> ApiException.badRequest("Reporting manager '" + name + "' does not exist"));
        if (manager.getUserId().equals(forUserId)) {
            throw ApiException.badRequest("A user cannot be their own reporting manager");
        }
        if (!manager.isActive()) {
            throw ApiException.badRequest("Reporting manager '" + name + "' is inactive");
        }
        if (forUserId != null && routing.isSelfOrBelow(forUserId, manager.getUserId())) {
            throw ApiException.badRequest("'" + name + "' is in this person's own team, so they cannot be their manager");
        }
        return manager;
    }

    /** Active users who can approve - the choices for "reporting manager". */
    @Transactional(readOnly = true)
    public List<UserOption> approvers() {
        SecurityUtils.require(Permissions.MANAGE_USERS);
        return approversUnchecked();
    }

    @Transactional(readOnly = true)
    public List<UserOption> approversUnchecked() {
        List<UserOption> result = new ArrayList<>();
        for (User u : userRepository.findAllById(routing.availableApprovers())) {
            result.add(option(u));
        }
        result.sort((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()));
        return result;
    }

    public UserResponse toResponse(User u) {
        UserGroup ug = userGroupRepository.findById(u.getUserId()).orElse(null);
        Group g = ug == null ? null : groupRepository.findById(ug.getGroupId()).orElse(null);
        User manager = u.getReportingManagerId() == null ? null : userRepository.findById(u.getReportingManagerId()).orElse(null);
        return toResponse(u, g, manager);
    }

    private static UserResponse toResponse(User u, Group g, User manager) {
        UserResponse r = new UserResponse();
        r.setReportingManagerId(u.getReportingManagerId());
        r.setReportingManagerUsername(manager == null ? null : manager.getUsername());
        r.setReportingManagerName(manager == null ? null : manager.getDisplayName());
        r.setEmployeeCode(u.getEmployeeCode());
        r.setDesignation(u.getDesignation());
        r.setMustChangePassword(u.isPasswordChangeRequired());
        r.setUserId(u.getUserId());
        r.setUsername(u.getUsername());
        r.setEmail(u.getEmail());
        r.setFirstName(u.getFirstName());
        r.setLastName(u.getLastName());
        r.setStatus(u.getStatus());
        r.setGroupId(g == null ? null : g.getGroupId());
        r.setGroupCode(g == null ? null : g.getGroupCode());
        r.setGroupName(g == null ? null : g.getGroupName());
        r.setCreatedAt(u.getCreatedAt());
        r.setUpdatedAt(u.getUpdatedAt());
        return r;
    }
}
