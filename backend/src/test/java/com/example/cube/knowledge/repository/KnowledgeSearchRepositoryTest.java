package com.example.cube.knowledge.repository;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeSearchRepositoryTest {

    private static final String BS = "\\";

    @Test
    void buildsAPrefixQueryWithEveryCharacterEscaped() {
        assertEquals("(" + BS + "f" + BS + "l" + BS + "e" + BS + "x%)",
                KnowledgeSearchRepository.containsQuery(Collections.singletonList("flex")));
    }

    @Test
    void joinsTermsWithAndAndNeutralisesOperatorWords() {
        String q = KnowledgeSearchRepository.containsQuery(Arrays.asList("oracle", "or"));
        assertEquals("(" + BS + "o" + BS + "r" + BS + "a" + BS + "c" + BS + "l" + BS + "e%) AND (" + BS + "o" + BS + "r%)", q);
    }

    @Test
    void splitsTheQueryIntoCleanTerms() {
        assertEquals(Arrays.asList("vpn", "setup"), KnowledgeSearchRepository.terms("  vpn {setup}  "));
        assertEquals(Collections.emptyList(), KnowledgeSearchRepository.terms("--- !!"));
    }
}
