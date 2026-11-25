package com.example.cube.knowledge.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AttachmentResponse {

    private Long attachmentId;
    private Long knowledgeId;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private Long uploadedBy;
    private LocalDateTime createdAt;
    /** Relative URLs; call them with fetch + Authorization header and read the body as a blob. */
    private String downloadUrl;
    private String previewUrl;
}
