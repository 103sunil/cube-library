package com.example.cube.knowledge.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

/** Shared keyword lookup; items link to keywords through CU_KNOWLEDGE_KEYWORD. */
@Entity
@Table(name = "CU_KEYWORD")
@Getter
@Setter
public class Keyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "KEYWORD_ID")
    private Long keywordId;

    @Column(name = "KEYWORD_NAME", nullable = false, length = 100, unique = true)
    private String keywordName;
}
