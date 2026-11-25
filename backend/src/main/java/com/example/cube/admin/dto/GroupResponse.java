package com.example.cube.admin.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class GroupResponse {

    private Long groupId;
    private String groupCode;
    private String groupName;
    private String description;
    private String status;
    private long memberCount;
    private LocalDateTime createdAt;
}
