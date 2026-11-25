package com.example.cube.audit;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CU_AUDIT_LOG")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AUDIT_ID")
    private Long auditId;

    @Column(name = "ACTOR", length = 50)
    private String actor;

    @Column(name = "ACTION", nullable = false, length = 50)
    private String action;

    @Column(name = "ENTITY_TYPE", length = 50)
    private String entityType;

    @Column(name = "ENTITY_ID", length = 100)
    private String entityId;

    @Column(name = "DETAILS", length = 1000)
    private String details;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
