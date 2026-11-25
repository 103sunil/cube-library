package com.example.cube.auth.entity;

import com.example.cube.common.RecordStatus;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

/** A user belongs to exactly one group (USER_ID is the key). */
@Entity
@Table(name = "CU_USER_GROUP")
@Getter
@Setter
public class UserGroup {

    @Id
    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "GROUP_ID", nullable = false)
    private Long groupId;

    @Column(name = "STATUS", length = 1)
    private String status;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    public boolean isActive() {
        return RecordStatus.ACTIVE.equals(status);
    }
}
