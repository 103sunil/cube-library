package com.example.cube.auth.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LoginResponse {

    private String token;
    private String tokenType = "Bearer";
    private long expiresIn;
    private String username;
    private String groupCode;
    private List<String> permissions;
    /** True when the user signed in with a temporary password and must set their own. */
    private boolean mustChangePassword;
    private String message;
}
