package com.example.cube.auth.service.impl;

import com.example.cube.auth.Permissions;
import com.example.cube.auth.entity.Access;
import com.example.cube.auth.entity.Group;
import com.example.cube.auth.entity.GroupAccessTemplate;
import com.example.cube.auth.entity.Module;
import com.example.cube.auth.entity.User;
import com.example.cube.auth.entity.UserAccess;
import com.example.cube.auth.entity.UserGroup;
import com.example.cube.auth.repository.AccessRepository;
import com.example.cube.auth.repository.GroupAccessTemplateRepository;
import com.example.cube.auth.repository.GroupRepository;
import com.example.cube.auth.repository.ModuleRepository;
import com.example.cube.auth.repository.UserAccessRepository;
import com.example.cube.auth.repository.UserGroupRepository;
import com.example.cube.auth.repository.UserRepository;
import com.example.cube.auth.service.PermissionService;
import com.example.cube.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

@Service
@Transactional(readOnly = true)
public class PermissionServiceImpl implements PermissionService {

    private final UserGroupRepository userGroupRepository;
    private final GroupRepository groupRepository;
    private final GroupAccessTemplateRepository groupAccessRepository;
    private final UserAccessRepository userAccessRepository;
    private final ModuleRepository moduleRepository;
    private final AccessRepository accessRepository;
    private final UserRepository userRepository;

    public PermissionServiceImpl(UserGroupRepository userGroupRepository,
                                 GroupRepository groupRepository,
                                 GroupAccessTemplateRepository groupAccessRepository,
                                 UserAccessRepository userAccessRepository,
                                 ModuleRepository moduleRepository,
                                 AccessRepository accessRepository,
                                 UserRepository userRepository) {
        this.userGroupRepository = userGroupRepository;
        this.groupRepository = groupRepository;
        this.groupAccessRepository = groupAccessRepository;
        this.userAccessRepository = userAccessRepository;
        this.moduleRepository = moduleRepository;
        this.accessRepository = accessRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Set<Long> usersWithPermission(String permission) {
        Set<Long> result = new HashSet<>();
        int idx = permission.indexOf(':');
        Optional<Module> module = moduleRepository.findByModuleCode(permission.substring(0, idx));
        if (!module.isPresent() || !module.get().isActive()) {
            return result;
        }
        Optional<Access> access = accessRepository.findByModuleIdAndAccessCode(
                module.get().getModuleId(), permission.substring(idx + 1));
        if (!access.isPresent() || !access.get().isActive()) {
            return result;
        }
        Long accessId = access.get().getAccessId();

        // Members of active groups that allow it
        Set<Long> groupIds = new HashSet<>();
        for (GroupAccessTemplate grant : groupAccessRepository.findByIdAccessId(accessId)) {
            if (grant.isActive() && grant.isAllowed()) {
                groupIds.add(grant.getId().getGroupId());
            }
        }
        if (!groupIds.isEmpty()) {
            Set<Long> activeGroups = new HashSet<>();
            for (Group g : groupRepository.findAllById(groupIds)) {
                if (g.isActive()) {
                    activeGroups.add(g.getGroupId());
                }
            }
            if (!activeGroups.isEmpty()) {
                for (UserGroup membership : userGroupRepository.findByGroupIdIn(activeGroups)) {
                    if (membership.isActive()) {
                        result.add(membership.getUserId());
                    }
                }
            }
        }

        // Unexpired overrides add or remove it
        LocalDate today = LocalDate.now();
        for (UserAccess override : userAccessRepository.findByIdAccessId(accessId)) {
            if (override.isEffectiveOn(today)) {
                if (override.isAllowed()) {
                    result.add(override.getId().getUserId());
                } else {
                    result.remove(override.getId().getUserId());
                }
            }
        }

        // Only active users
        Set<Long> active = new HashSet<>();
        if (!result.isEmpty()) {
            for (User u : userRepository.findAllById(result)) {
                if (u.isActive()) {
                    active.add(u.getUserId());
                }
            }
        }
        return active;
    }

    @Override
    public Set<String> effectivePermissions(Long userId) {
        Map<Long, String> modules = new HashMap<>();
        for (Module m : moduleRepository.findAll()) {
            if (m.isActive()) {
                modules.put(m.getModuleId(), m.getModuleCode());
            }
        }
        // accessId -> "MODULE:ACCESS" for active access codes in active modules
        Map<Long, String> codes = new HashMap<>();
        for (Access a : accessRepository.findAll()) {
            String module = modules.get(a.getModuleId());
            if (a.isActive() && module != null) {
                codes.put(a.getAccessId(), Permissions.code(module, a.getAccessCode()));
            }
        }

        Set<String> permissions = new TreeSet<>();

        Optional<Group> group = findActiveGroup(userId);
        if (group.isPresent()) {
            for (GroupAccessTemplate grant : groupAccessRepository.findByIdGroupId(group.get().getGroupId())) {
                if (grant.isActive() && grant.isAllowed()) {
                    String code = codes.get(grant.getId().getAccessId());
                    if (code != null) {
                        permissions.add(code);
                    }
                }
            }
        }

        LocalDate today = LocalDate.now();
        for (UserAccess override : userAccessRepository.findByIdUserId(userId)) {
            if (!override.isEffectiveOn(today)) {
                continue;
            }
            String code = codes.get(override.getId().getAccessId());
            if (code == null) {
                continue;
            }
            if (override.isAllowed()) {
                permissions.add(code);
            } else {
                permissions.remove(code);
            }
        }
        return permissions;
    }

    @Override
    public Optional<Group> findActiveGroup(Long userId) {
        Optional<UserGroup> membership = userGroupRepository.findById(userId);
        if (!membership.isPresent() || !membership.get().isActive()) {
            return Optional.empty();
        }
        Optional<Group> group = groupRepository.findById(membership.get().getGroupId());
        return group.isPresent() && group.get().isActive() ? group : Optional.<Group>empty();
    }

    @Override
    public CurrentUser buildCurrentUser(User user, String tokenId, Date tokenExpiry) {
        String groupCode = findActiveGroup(user.getUserId()).map(Group::getGroupCode).orElse(null);
        return new CurrentUser(user.getUserId(), user.getUsername(), groupCode,
                effectivePermissions(user.getUserId()), tokenId, tokenExpiry);
    }
}
