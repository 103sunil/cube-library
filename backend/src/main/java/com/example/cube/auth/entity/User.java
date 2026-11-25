package com.example.cube.auth.entity;

import com.example.cube.common.RecordStatus;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity(name = "AppUser")
@Table(name = "CU_USER_MASTER")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "USERNAME", nullable = false, length = 100)
    private String username;

    @Column(name = "EMAIL", nullable = false, length = 150)
    private String email;

    @Column(name = "PASSWORD_HASH", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "FIRST_NAME", length = 100)
    private String firstName;

    @Column(name = "LAST_NAME", length = 100)
    private String lastName;

    @Column(name = "STATUS", length = 1)
    private String status;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    /** USER_ID of the manager who reviews this user's submissions (null = shared queue). */
    @Column(name = "REPORTING_MANAGER_ID")
    private Long reportingManagerId;

    /** Company employee ID (unique when set). */
    @Column(name = "EMPLOYEE_CODE", length = 30)
    private String employeeCode;

    /** Job title shown in the app, e.g. Team Lead. Not a permission. */
    @Column(name = "DESIGNATION", length = 100)
    private String designation;

    /** Y = must set a new password at next login (temporary password given). */
    @Column(name = "MUST_CHANGE_PASSWORD", length = 1)
    private String mustChangePassword = "N";

    public boolean isPasswordChangeRequired() {
        return "Y".equals(mustChangePassword);
    }

    public boolean isActive() {
        return RecordStatus.ACTIVE.equals(status);
    }

    public String getDisplayName() {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String full = (first + " " + last).trim();
        return full.isEmpty() ? username : full;
    }
}
