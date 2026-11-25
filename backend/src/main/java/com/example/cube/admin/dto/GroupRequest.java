package com.example.cube.admin.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** groupCode is used on create only; it cannot be changed afterwards. */
@Getter
@Setter
public class GroupRequest {

    @Size(max = 30)
    private String groupCode;

    @Size(max = 100)
    private String groupName;

    @Size(max = 500)
    private String description;

    @Pattern(regexp = "[AI]", message = "Status must be A or I")
    private String status;
}
