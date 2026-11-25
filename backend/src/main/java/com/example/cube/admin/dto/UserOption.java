package com.example.cube.admin.dto;

import lombok.Getter;
import lombok.Setter;

/** A user offered in a picker (e.g. possible reporting managers). */
@Getter
@Setter
public class UserOption {

    private final Long userId;
    private final String username;
    private final String displayName;
    /** Job title, e.g. Team Lead (may be null). */
    private String designation;
    /** For "My team" pickers: true when the person is the manager or in their hierarchy. */
    private Boolean inMyTeam;
    /** Place on the reporting ladder (see Levels): 3 = Team Lead, 4 = Manager, ... */
    private Integer level;

    public UserOption(Long userId, String username, String displayName) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
    }
}
