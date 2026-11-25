package com.example.cube.auth.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;

/** (GROUP_ID, ACCESS_ID); the module comes from the access code. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class GroupAccessTemplateId implements Serializable {

    private static final long serialVersionUID = 2L;

    @Column(name = "GROUP_ID")
    private Long groupId;

    @Column(name = "ACCESS_ID")
    private Long accessId;
}
