package com.example.cube.team;

import com.example.cube.admin.dto.CreateUserRequest;
import com.example.cube.admin.dto.UpdateUserRequest;
import com.example.cube.admin.dto.UserOption;
import com.example.cube.admin.dto.UserResponse;
import com.example.cube.admin.service.AdminUserService;
import com.example.cube.auth.Levels;
import com.example.cube.auth.Permissions;
import com.example.cube.auth.entity.User;
import com.example.cube.auth.repository.UserRepository;
import com.example.cube.common.exception.ApiException;
import com.example.cube.knowledge.service.ReviewRouting;
import com.example.cube.security.CurrentUser;
import com.example.cube.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "My team" for managers (KNOWLEDGE:APPROVE): everyone below them in the reporting line, including
 * people under their team leads. They can add people reporting to themselves or to anyone in their team,
 * edit, reset passwords, move people (also to another manager) and delete leavers. New users always get
 * employee access. Administrators use /admin/users.
 */
@Service
@Transactional
public class TeamService {

    static final String EMPLOYEE_GROUP = "EMPLOYEE";

    private final AdminUserService users;
    private final UserRepository userRepository;
    private final ReviewRouting routing;

    public TeamService(AdminUserService users, UserRepository userRepository, ReviewRouting routing) {
        this.users = users;
        this.userRepository = userRepository;
        this.routing = routing;
    }
    /** Everyone under me: direct reports and their teams (e.g. people under my team leads). */
    @Transactional(readOnly = true)
    public List<UserResponse> myTeam() {
        CurrentUser me = SecurityUtils.require(Permissions.KNOWLEDGE_APPROVE);
        List<UserResponse> result = new ArrayList<>();
        for (User u : routing.everyoneBelow(me.getUserId())) {
            result.add(users.toResponse(u));
        }
        return result;
    }

    /**
     * Choices for "Reports to": me and active Team Leads or higher in my hierarchy first (inMyTeam = true),
     * then other managers (choosing one moves the person out of my team).
     */
    @Transactional(readOnly = true)
    public List<UserOption> managers() {
        CurrentUser me = SecurityUtils.require(Permissions.KNOWLEDGE_APPROVE);
        List<UserOption> result = new ArrayList<>();
        Set<Long> mine = new HashSet<>();
        userRepository.findById(me.getUserId()).ifPresent(u -> {
            UserOption o = users.option(u);
            o.setInMyTeam(true);
            result.add(o);
            mine.add(u.getUserId());
        });
        for (User u : routing.everyoneBelow(me.getUserId())) {
            if (u.isActive() && users.levelOf(u) >= Levels.TEAM_LEAD) {
                UserOption o = users.option(u);
                o.setInMyTeam(true);
                result.add(o);
                mine.add(u.getUserId());
            }
        }
        for (UserOption manager : users.approversUnchecked()) {
            if (!mine.contains(manager.getUserId())) {
                manager.setInMyTeam(false);
                result.add(manager);
            }
        }
        return result;
    }

    public UserResponse create(CreateUserRequest request) {
        CurrentUser me = SecurityUtils.require(Permissions.KNOWLEDGE_APPROVE);
        request.setGroupCode(EMPLOYEE_GROUP);
        String manager = request.getReportingManagerUsername() == null ? "" : request.getReportingManagerUsername().trim();
        if (manager.isEmpty()) {
            request.setReportingManagerUsername(me.getUsername());
        } else if (!isMeOrMyTeam(me, manager)) {
            throw ApiException.forbidden("New people can only report to you or someone in your team");
        }
        return users.createUnchecked(request);
    }

    public UserResponse update(Long id, UpdateUserRequest request) {
        CurrentUser me = requireInMyTeam(id);
        request.setGroupCode(null); // managers cannot change access
        if (request.getReportingManagerUsername() != null) {
            String manager = request.getReportingManagerUsername().trim();
            if (manager.isEmpty()) {
                throw ApiException.badRequest("Choose who this person reports to");
            }
            boolean otherManager = userRepository.findByUsername(manager)
                    .map(u -> users.approversUnchecked().stream().anyMatch(o -> o.getUserId().equals(u.getUserId())))
                    .orElse(false);
            if (!isMeOrMyTeam(me, manager) && !otherManager) {
                throw ApiException.forbidden("Choose yourself, someone in your team or another manager");
            }
        }
        return users.updateUnchecked(id, request, me);
    }

    /** A manager removes someone in their team who left the company. */
    public Map<String, Object> remove(Long id) {
        CurrentUser me = requireInMyTeam(id);
        return users.removeUnchecked(id, me);
    }

    public void resetPassword(Long id, String password) {
        requireInMyTeam(id);
        users.resetPasswordUnchecked(id, password);
    }

    private boolean isMeOrMyTeam(CurrentUser me, String username) {
        return userRepository.findByUsername(username)
                .map(u -> u.isActive() && routing.isSelfOrBelow(me.getUserId(), u.getUserId()))
                .orElse(false);
    }

    /** The target must be somewhere below me in the reporting line. */
    private CurrentUser requireInMyTeam(Long id) {
        CurrentUser me = SecurityUtils.require(Permissions.KNOWLEDGE_APPROVE);
        User target = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        if (target.getUserId().equals(me.getUserId()) || !routing.isSelfOrBelow(me.getUserId(), target.getUserId())) {
            throw ApiException.forbidden("You can only change people in your team");
        }
        return me;
    }
}
