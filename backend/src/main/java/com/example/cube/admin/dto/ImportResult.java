package com.example.cube.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Outcome of a bulk user import. If errors is not empty, nothing was created. */
@Getter
@Setter
public class ImportResult {

    private int created;
    private List<String> errors = new ArrayList<>();
    /** Only when created: the temporary passwords to hand out (shown once, stored only as hashes). */
    private List<CreatedUser> users = new ArrayList<>();

    @Getter
    @AllArgsConstructor
    public static class CreatedUser {
        private final String username;
        private final String employeeCode;
        private final String group;
        private final String reportingManager;
        private final String temporaryPassword;
    }
}
