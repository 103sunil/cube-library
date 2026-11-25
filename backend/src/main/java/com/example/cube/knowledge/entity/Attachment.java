package com.example.cube.knowledge.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Attachment metadata. Where the bytes are:
 *  - STORAGE key in ONEDRIVE_FILE_ID (new uploads: GitHub / local storage path)
 *  - old uploads: an absolute or "knowledge-N/..." path written by the previous version
 *  - ONEDRIVE_FILE_ID empty: bytes in the FILE_DATA BLOB (read on demand, not mapped here)
 */
@Entity
@Table(name = "CU_ATTACHMENT")
@Getter
@Setter
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ATTACHMENT_ID")
    private Long attachmentId;

    @Column(name = "KNOWLEDGE_ID", nullable = false)
    private Long knowledgeId;

    @Column(name = "FILE_NAME", length = 255)
    private String fileName;

    @Column(name = "CONTENT_TYPE", length = 100)
    private String contentType;

    @Column(name = "FILE_SIZE")
    private Long fileSize;

    @Column(name = "ONEDRIVE_FILE_ID", length = 255)
    private String storageKey;

    @Column(name = "UPLOADED_BY")
    private Long uploadedBy;

    @Column(name = "UPLOADED_AT")
    private LocalDateTime createdAt;
}
