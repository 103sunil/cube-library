package com.example.cube.admin.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/** Group grant or user override. expiryDate is only used for user overrides. */
@Getter
@Setter
public class GrantRequest {

    @NotBlank(message = "moduleCode is required")
    private String moduleCode;

    @NotBlank(message = "accessCode is required")
    private String accessCode;

    /** true = grant, false = explicitly deny. */
    @NotNull(message = "allowed is required")
    private Boolean allowed;

    private LocalDate expiryDate;
}
