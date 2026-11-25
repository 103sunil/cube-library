package com.example.cube.auth.entity;

import com.example.cube.common.RecordStatus;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

/** Entity name is AppGroup because GROUP is a reserved word in JPQL. */
@Entity(name = "AppGroup")
@Table(name = "CU_GROUP_MASTER")
@Getter
@Setter
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "GROUP_ID")
    private Long groupId;

    @Column(name = "GROUP_CODE", nullable = false, length = 50)
    private String groupCode;

    @Column(name = "GROUP_NAME", nullable = false, length = 100)
    private String groupName;

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
