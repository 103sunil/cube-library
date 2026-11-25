package com.example.cube.knowledge.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

/** A knowledge item a user saved for later (CU_BOOKMARK). */
@Entity
@Table(name = "CU_BOOKMARK")
@Getter
@Setter
@NoArgsConstructor
public class Bookmark {

    @EmbeddedId
    private Id id;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    public Bookmark(Long userId, Long knowledgeId) {
        this.id = new Id(userId, knowledgeId);
        this.createdAt = LocalDateTime.now();
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {

        private static final long serialVersionUID = 1L;

        @Column(name = "USER_ID")
        private Long userId;

        @Column(name = "KNOWLEDGE_ID")
        private Long knowledgeId;
    }
}
