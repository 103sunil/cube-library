package com.example.cube.knowledge.entity;

/**
 * Lifecycle:
 *   DRAFT -> PENDING -> PUBLISHED -> ARCHIVED (-> PUBLISHED via restore)
 *   PENDING -> REJECTED -> (owner edits) -> PENDING
 * Submissions by users holding KNOWLEDGE:APPROVE skip PENDING and publish directly.
 */
public enum KnowledgeStatus {
    DRAFT,
    PENDING,
    PUBLISHED,
    REJECTED,
    ARCHIVED
}
