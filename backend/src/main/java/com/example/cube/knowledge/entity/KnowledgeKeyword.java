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

/** Link between a knowledge item and a keyword (CU_KNOWLEDGE_KEYWORD). */
@Entity
@Table(name = "CU_KNOWLEDGE_KEYWORD")
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeKeyword {

    @EmbeddedId
    private Id id;

    public KnowledgeKeyword(Long keywordId, Long knowledgeId) {
        this.id = new Id(keywordId, knowledgeId);
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {

        private static final long serialVersionUID = 1L;

        @Column(name = "KEYWORD_ID")
        private Long keywordId;

        @Column(name = "KNOWLEDGE_ID")
        private Long knowledgeId;
    }
}
