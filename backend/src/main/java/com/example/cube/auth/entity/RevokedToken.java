package com.example.cube.auth.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** A logged-out JWT (by its jti). Rows can be purged once EXPIRES_AT has passed. */
@Entity
@Table(name = "CU_REVOKED_TOKEN")
@Getter
@Setter
public class RevokedToken {

    @Id
    @Column(name = "TOKEN_ID", length = 64)
    private String tokenId;

    @Column(name = "USERNAME", length = 50)
    private String username;

    @Column(name = "EXPIRES_AT", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "REVOKED_AT", nullable = false)
    private LocalDateTime revokedAt;
}
