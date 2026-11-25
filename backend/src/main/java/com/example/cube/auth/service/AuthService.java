package com.example.cube.auth.service;

import com.example.cube.auth.dto.request.LoginRequest;
import com.example.cube.auth.dto.response.CurrentUserResponse;
import com.example.cube.auth.dto.response.LoginResponse;
import com.example.cube.auth.dto.response.PermissionsResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    void logout();

    CurrentUserResponse getCurrentUser();

    PermissionsResponse getCurrentPermissions();

    /** Changes the signed-in user's own password and clears "must change password". */
    void changePassword(String currentPassword, String newPassword);
}
