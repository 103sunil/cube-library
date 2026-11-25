package com.example.cube.auth.entity;

import com.example.cube.common.RecordStatus;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * An access code that belongs to one module (e.g. KNOWLEDGE / APPROVE).
 * The permission string is MODULE_CODE:ACCESS_CODE.
 */
@Entity
@Table(name = "CU_ACCESS_MASTER")
@Getter
@Setter
public class Access {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ACCESS_ID")
    private Long accessId;

    @Column(name = "MODULE_ID", nullable = false)
    private Long moduleId;

    @Column(name = "ACCESS_CODE", nullable = false, length = 50)
    private String accessCode;

    @Column(name = "DESCRIPTION", length = 255)
    private String description;

    @Column(name = "STATUS", length = 1)
    private String status;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    public boolean isActive() {
        return RecordStatus.ACTIVE.equals(status);
    }
}
