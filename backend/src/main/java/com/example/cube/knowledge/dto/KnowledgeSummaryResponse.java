package com.example.cube.knowledge.dto;

import com.example.cube.knowledge.entity.KnowledgeStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class KnowledgeSummaryResponse {

    private Long knowledgeId;
    private String title;
    /** First ~220 characters of the description, on one line, for list cards. */
    private String summary;
    private KnowledgeStatus status;
    private Long ownerId;
    private String ownerUsername;
    private String ownerName;
    private List<String> keywords;
    private long attachmentCount;
    private String rejectReason;
    /** Pending only: the reporting manager who must review it (null = any approver). */
    private Long assignedReviewerId;
    private String assignedReviewerName;
    private boolean assignedToMe;
    /** True if the current user bookmarked it. */
    private boolean bookmarked;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;
}
