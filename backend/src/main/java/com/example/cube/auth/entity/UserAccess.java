package com.example.cube.auth.entity;

import com.example.cube.common.RecordStatus;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Per-user override of the group defaults. ALLOWED=Y grants, ALLOWED=N revokes.
 * Ignored after EXPIRY_DATE (inclusive). A null expiry never expires.
 */
@Entity
@Table(name = "CU_USER_ACCESS")
@Getter
@Setter
public class UserAccess {

    @EmbeddedId
    private UserAccessId id;

    @Column(name = "ALLOWED", length = 1)
    private String allowed;

    @Column(name = "EXPIRY_DATE")
    private LocalDate expiryDate;

    @Column(name = "STATUS", length = 1)
    private String status;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    public boolean isAllowed() {
        return RecordStatus.YES.equals(allowed);
    }

    public boolean isEffectiveOn(LocalDate date) {
        return RecordStatus.ACTIVE.equals(status) && (expiryDate == null || !expiryDate.isBefore(date));
    }
}
