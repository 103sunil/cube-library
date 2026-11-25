package com.example.cube.auth.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;

/** (USER_ID, ACCESS_ID); the module comes from the access code. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserAccessId implements Serializable {

    private static final long serialVersionUID = 2L;

    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "ACCESS_ID")
    private Long accessId;
}
