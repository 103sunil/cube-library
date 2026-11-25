package com.example.cube.admin.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class UserResponse {

    private Long userId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String status;
    private Long groupId;
    private String groupCode;
    private String groupName;
    private Long reportingManagerId;
    private String reportingManagerUsername;
    private String reportingManagerName;
    private String employeeCode;
    private String designation;
    private boolean mustChangePassword;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
