package com.example.cube.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CatalogItemResponse {

    private Long id;
    private String code;
    /** Access codes only. */
    private String moduleCode;
    /** Access codes only: MODULE:ACCESS. */
    private String permission;
    private String name;
    private String description;
    private String status;
    private LocalDateTime createdAt;
}
