package com.example.cube.admin.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Email;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** Fields left null are not changed. */
@Getter
@Setter
public class UpdateUserRequest {

    @Email(message = "Email is not valid")
    @Size(max = 100)
    private String email;

    @Size(max = 50)
    private String firstName;

    @Size(max = 50)
    private String lastName;

    private String groupCode;

    /** Reporting manager username: null = unchanged, "" = remove, otherwise set. */
    private String reportingManagerUsername;

    /** Employee ID: null = unchanged, "" = remove. */
    @Size(max = 30)
    private String employeeCode;

    /** Job title: null = unchanged, "" = remove. */
    @Size(max = 100)
    private String designation;

    @Pattern(regexp = "[AI]", message = "Status must be A or I")
    private String status;
}
