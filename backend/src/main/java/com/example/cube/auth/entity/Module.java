package com.example.cube.auth.entity;

import com.example.cube.common.RecordStatus;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CU_MODULE_MASTER")
@Getter
@Setter
public class Module {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MODULE_ID")
    private Long moduleId;

    @Column(name = "MODULE_CODE", nullable = false, length = 50)
    private String moduleCode;

    @Column(name = "MODULE_NAME", length = 100)
    private String moduleName;

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
