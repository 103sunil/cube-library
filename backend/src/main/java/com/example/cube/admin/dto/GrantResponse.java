package com.example.cube.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GrantResponse {

    private String moduleCode;
    private String accessCode;
    private String permission;
    private boolean allowed;
    private String status;
    /** User overrides only. */
    private LocalDate expiryDate;
    /** User overrides only: active and not expired today. */
    private Boolean effective;
}
