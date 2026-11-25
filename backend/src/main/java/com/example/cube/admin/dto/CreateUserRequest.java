package com.example.cube.admin.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Getter
@Setter
public class CreateUserRequest {

    @NotBlank(message = "Username is required")
    @Size(max = 50)
    @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Username may contain letters, digits, '.', '_' and '-' only")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email is not valid")
    @Size(max = 100)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be 8 to 100 characters")
    private String password;

    @Size(max = 50)
    private String firstName;

    @Size(max = 50)
    private String lastName;

    private String groupCode;

    /** Username of the reporting manager who reviews this user's submissions (optional). */
    private String reportingManagerUsername;

    /** Company employee ID (optional, unique). */
    @Size(max = 30)
    private String employeeCode;

    /** Job title, e.g. Team Lead (optional). */
    @Size(max = 100)
    private String designation;

    /** A (active, default) or I (inactive). */
    @Pattern(regexp = "[AI]", message = "Status must be A or I")
    private String status;
}
