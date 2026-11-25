package com.example.cube.auth.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Drives show/hide in the UI. "permissions" is the flat list ("KNOWLEDGE:VIEW", ...);
 * "modules" groups the same data by module ({"KNOWLEDGE": ["VIEW", ...]}).
 */
@Getter
@Setter
public class PermissionsResponse {

    private String username;
    private String groupCode;
    private List<String> permissions;
    private Map<String, List<String>> modules;
}
