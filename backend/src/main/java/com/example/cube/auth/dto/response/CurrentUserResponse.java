package com.example.cube.auth.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CurrentUserResponse {

    private Long userId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String displayName;
    private String groupCode;
    private String groupName;
    private String employeeCode;
    private String designation;
    private boolean mustChangePassword;
}
