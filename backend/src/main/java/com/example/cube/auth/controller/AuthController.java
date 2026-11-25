package com.example.cube.auth.controller;

import com.example.cube.auth.dto.request.ChangePasswordRequest;
import com.example.cube.auth.dto.request.LoginRequest;
import com.example.cube.auth.dto.response.CurrentUserResponse;
import com.example.cube.auth.dto.response.LoginResponse;
import com.example.cube.auth.dto.response.PermissionsResponse;
import com.example.cube.auth.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Map<String, String>> logout() {
        authService.logout();
        return ResponseEntity.ok(Collections.singletonMap("message", "Logged out"));
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> currentUser() {
        return ResponseEntity.ok(authService.getCurrentUser());
    }

    /** Change my own password (also used for the forced change after a temporary password). */
    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/permissions")
    public ResponseEntity<PermissionsResponse> currentPermissions() {
        return ResponseEntity.ok(authService.getCurrentPermissions());
    }
}
