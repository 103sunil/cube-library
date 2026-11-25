package com.example.cube.admin.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * Module or access code. "code" (and "moduleCode" for access codes) are used on create only.
 * For access codes "name" is stored as the description.
 */
@Getter
@Setter
public class CatalogItemRequest {

    @Size(max = 50)
    private String code;

    /** Access codes only: the module the code belongs to. */
    @Size(max = 50)
    private String moduleCode;

    @Size(max = 100)
    private String name;

    @Size(max = 255)
    private String description;

    @Pattern(regexp = "[AI]", message = "Status must be A or I")
    private String status;
}
