package com.example.cube.knowledge.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Full replacement of title, description and keywords. Validation happens in the service. */
@Getter
@Setter
public class UpdateKnowledgeRequest {

    private String title;
    private String description;
    private List<String> keywords;
}
