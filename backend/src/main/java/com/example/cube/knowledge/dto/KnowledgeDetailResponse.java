package com.example.cube.knowledge.dto;

import com.example.cube.knowledge.entity.KnowledgeStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class KnowledgeDetailResponse {

    private Long knowledgeId;
    private String title;
    private String description;
    private KnowledgeStatus status;
    private Long ownerId;
    private String ownerUsername;
    private String ownerName;
    private List<String> keywords;
    private List<AttachmentResponse> attachments;
    private int attachmentCount;
    private String rejectReason;
    /** Pending only: the reporting manager who must review it (null = any approver). */
    private Long assignedReviewerId;
    private String assignedReviewerName;
    private boolean assignedToMe;
    /** True if the current user bookmarked it. */
    private boolean bookmarked;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private KnowledgeActions actions;
}
