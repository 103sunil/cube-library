package com.example.cube.auth.entity;

import com.example.cube.common.RecordStatus;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

/** Default grant for every member of a group: (group, module, access) -> allowed Y/N. */
@Entity
@Table(name = "CU_GROUP_ACCESS_TEMPLATE")
@Getter
@Setter
public class GroupAccessTemplate {

    @EmbeddedId
    private GroupAccessTemplateId id;

    @Column(name = "ALLOWED", length = 1)
    private String allowed;

    @Column(name = "STATUS", length = 1)
    private String status;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    public boolean isActive() {
        return RecordStatus.ACTIVE.equals(status);
    }

    public boolean isAllowed() {
        return RecordStatus.YES.equals(allowed);
    }
}
