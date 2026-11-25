package com.example.cube.auth.service;

import com.example.cube.audit.AuditService;
import com.example.cube.auth.dto.request.LoginRequest;
import com.example.cube.auth.dto.response.LoginResponse;
import com.example.cube.auth.entity.User;
import com.example.cube.auth.repository.UserRepository;
import com.example.cube.auth.service.impl.AuthServiceImpl;
import com.example.cube.common.exception.ApiException;
import com.example.cube.security.JwtUtil;
import com.example.cube.security.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private UserRepository userRepository;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.findActiveGroup(anyLong())).thenReturn(Optional.empty());
        when(permissionService.effectivePermissions(anyLong())).thenReturn(Collections.singleton("KNOWLEDGE:VIEW"));

        authService = new AuthServiceImpl(userRepository, permissionService,
                new JwtUtil("unit-test-secret-unit-test-secret-0123456789", 60_000),
                passwordEncoder, new LoginAttemptService(5, 15),
                mock(TokenRevocationService.class), mock(AuditService.class));
    }

    @Test
    void loginReturnsTokenForValidCredentials() {
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(user("A")));

        LoginResponse response = authService.login(request("rahul", "secret"));

        assertNotNull(response.getToken());
        assertEquals("rahul", response.getUsername());
        assertEquals(Collections.singletonList("KNOWLEDGE:VIEW"), response.getPermissions());
    }

    @Test
    void loginRejectsWrongPassword() {
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(user("A")));

        ApiException ex = assertThrows(ApiException.class, () -> authService.login(request("rahul", "nope")));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void loginRejectsUnknownUserWithSameMessage() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> authService.login(request("ghost", "secret")));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("Invalid username or password", ex.getMessage());
    }

    @Test
    void loginBlocksInactiveUser() {
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(user("I")));

        ApiException ex = assertThrows(ApiException.class, () -> authService.login(request("rahul", "secret")));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    private User user(String status) {
        User user = new User();
        user.setUserId(1L);
        user.setUsername("rahul");
        user.setPasswordHash(passwordEncoder.encode("secret"));
        user.setEmail("rahul@example.com");
        user.setStatus(status);
        return user;
    }

    private static LoginRequest request(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }
}
