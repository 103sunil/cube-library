package com.example.cube.knowledge.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CU_KNOWLEDGE")
@Getter
@Setter
public class Knowledge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "KNOWLEDGE_ID")
    private Long knowledgeId;

    @Column(name = "TITLE", nullable = false, length = 255)
    private String title;

    @Lob
    @Column(name = "DESCRIPTION")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20)
    private KnowledgeStatus status;

    /** The author. */
    @Column(name = "CREATED_BY", nullable = false)
    private Long ownerId;

    @Lob
    @Column(name = "REJECTION_REASON")
    private String rejectReason;

    @Column(name = "REVIEWED_BY")
    private Long reviewedBy;

    @Column(name = "REVIEWED_AT")
    private LocalDateTime reviewedAt;

    @Column(name = "PUBLISHED_AT")
    private LocalDateTime publishedAt;

    /** Title + description + keywords, indexed by Oracle Text (CU_KNOWLEDGE_SEARCH_IDX). Maintained by the service. */
    @Lob
    @Column(name = "SEARCH_TEXT")
    private String searchText;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    /** Reviewer chosen when the item was (re)submitted: the author's reporting manager, or null = any approver. */
    @Column(name = "ASSIGNED_REVIEWER_ID")
    private Long assignedReviewerId;

    /**
     * Who may review it right now: the assigned reviewer if they can still approve, otherwise null
     * (shared queue). Not stored; filled in by ReviewRouting before access checks.
     */
    @Transient
    private Long effectiveReviewerId;
}
