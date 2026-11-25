package com.example.cube.auth.service.impl;

import com.example.cube.audit.AuditService;
import com.example.cube.auth.dto.request.LoginRequest;
import com.example.cube.auth.dto.response.CurrentUserResponse;
import com.example.cube.auth.dto.response.LoginResponse;
import com.example.cube.auth.dto.response.PermissionsResponse;
import com.example.cube.auth.entity.Group;
import com.example.cube.auth.entity.User;
import com.example.cube.auth.repository.UserRepository;
import com.example.cube.auth.service.AuthService;
import com.example.cube.auth.service.PermissionService;
import com.example.cube.auth.service.TokenRevocationService;
import com.example.cube.common.exception.ApiException;
import com.example.cube.security.CurrentUser;
import com.example.cube.security.JwtUtil;
import com.example.cube.security.LoginAttemptService;
import com.example.cube.security.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttempts;
    private final TokenRevocationService revocationService;
    private final AuditService auditService;
    /** Compared against when the username does not exist, so response time doesn't reveal valid usernames. */
    private final String dummyHash;

    public AuthServiceImpl(UserRepository userRepository, PermissionService permissionService, JwtUtil jwtUtil,
                           PasswordEncoder passwordEncoder, LoginAttemptService loginAttempts,
                           TokenRevocationService revocationService, AuditService auditService) {
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.loginAttempts = loginAttempts;
        this.revocationService = revocationService;
        this.auditService = auditService;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername().trim();

        long lockedFor = loginAttempts.lockedForSeconds(username);
        if (lockedFor > 0) {
            long minutes = Math.max(1, (lockedFor + 59) / 60);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed login attempts. Try again in " + minutes + " minute(s).");
        }

        Optional<User> found = userRepository.findByUsername(username);
        String hash = found.isPresent() ? found.get().getPasswordHash() : dummyHash;
        boolean passwordOk = passwordEncoder.matches(request.getPassword(), hash);

        if (!found.isPresent() || !passwordOk) {
            loginAttempts.recordFailure(username);
            auditService.recordAs(username, "LOGIN_FAILED", "USER", null, null);
            throw ApiException.unauthorized("Invalid username or password");
        }

        User user = found.get();
        if (com.example.cube.common.RecordStatus.REMOVED.equals(user.getStatus())) {
            auditService.recordAs(username, "LOGIN_BLOCKED_REMOVED", "USER", user.getUserId(), null);
            throw ApiException.forbidden("This account has been deleted. Please contact an administrator.");
        }
        if (!user.isActive()) {
            auditService.recordAs(username, "LOGIN_BLOCKED_INACTIVE", "USER", user.getUserId(), null);
            throw ApiException.forbidden("Your account is inactive. Please contact an administrator.");
        }

        loginAttempts.recordSuccess(username);
        JwtUtil.IssuedToken token = jwtUtil.generateToken(user.getUsername());
        auditService.recordAs(user.getUsername(), "LOGIN", "USER", user.getUserId(), null);

        LoginResponse response = new LoginResponse();
        response.setToken(token.getToken());
        response.setExpiresIn(jwtUtil.getExpirationSeconds());
        response.setUsername(user.getUsername());
        response.setGroupCode(permissionService.findActiveGroup(user.getUserId()).map(Group::getGroupCode).orElse(null));
        response.setPermissions(new ArrayList<>(permissionService.effectivePermissions(user.getUserId())));
        response.setMustChangePassword(user.isPasswordChangeRequired());
        response.setMessage("Login successful");
        return response;
    }

    @Override
    public void logout() {
        CurrentUser me = SecurityUtils.currentUser();
        revocationService.revoke(me.getTokenId(), me.getUsername(), me.getTokenExpiry());
        auditService.record("LOGOUT", "USER", me.getUserId(), null);
    }

    @Override
    public CurrentUserResponse getCurrentUser() {
        CurrentUser me = SecurityUtils.currentUser();
        User user = userRepository.findById(me.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("User not found"));
        Optional<Group> group = permissionService.findActiveGroup(user.getUserId());

        CurrentUserResponse response = new CurrentUserResponse();
        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setDisplayName(user.getDisplayName());
        response.setGroupCode(group.map(Group::getGroupCode).orElse(null));
        response.setGroupName(group.map(Group::getGroupName).orElse(null));
        response.setEmployeeCode(user.getEmployeeCode());
        response.setDesignation(user.getDesignation());
        response.setMustChangePassword(user.isPasswordChangeRequired());
        return response;
    }

    @Override
    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        CurrentUser me = SecurityUtils.currentUser();
        User user = userRepository.findById(me.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("Your current password is not correct");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("Choose a password different from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword("N");
        user.setUpdatedAt(java.time.LocalDateTime.now());
        userRepository.save(user);
        auditService.record("PASSWORD_CHANGE", "USER", user.getUserId(), null);
    }

    @Override
    public PermissionsResponse getCurrentPermissions() {
        CurrentUser me = SecurityUtils.currentUser();
        Map<String, List<String>> byModule = new TreeMap<>();
        for (String permission : me.getPermissions()) {
            int idx = permission.indexOf(':');
            String module = permission.substring(0, idx);
            String access = permission.substring(idx + 1);
            List<String> list = byModule.get(module);
            if (list == null) {
                list = new ArrayList<>();
                byModule.put(module, list);
            }
            list.add(access);
        }

        PermissionsResponse response = new PermissionsResponse();
        response.setUsername(me.getUsername());
        response.setGroupCode(me.getGroupCode());
        response.setPermissions(new ArrayList<>(me.getPermissions()));
        response.setModules(byModule);
        return response;
    }
}
