package com.example.cube.knowledge.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Getter
@Setter
public class RejectRequest {

    @NotBlank(message = "A rejection reason is required")
    @Size(max = 1000, message = "Reason must be 1000 characters or fewer")
    private String reason;
}
