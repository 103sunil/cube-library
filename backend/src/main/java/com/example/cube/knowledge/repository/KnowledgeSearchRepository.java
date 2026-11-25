package com.example.cube.knowledge.repository;

import com.example.cube.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Full-text search over PUBLISHED knowledge.
 *  - oracle-text: CONTAINS() on CU_KNOWLEDGE_SEARCH_IDX, matching the start of words, ranked by SCORE;
 *    falls back to "like" when that finds nothing (text inside a word) or fails.
 *  - like: case-insensitive LIKE on SEARCH_TEXT; slower, no index needed (tests / fallback).
 * Every term must match (AND).
 */
@Repository
public class KnowledgeSearchRepository {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSearchRepository.class);
    private static final int MAX_TERMS = 10;
    private static final int MAX_TERM_LENGTH = 64;

    @PersistenceContext
    private EntityManager entityManager;

    private final String mode;

    public KnowledgeSearchRepository(@Value("${cube.search.mode:oracle-text}") String mode) {
        this.mode = mode == null ? "oracle-text" : mode.trim().toLowerCase(Locale.ROOT);
    }

    public Page<Long> searchPublishedIds(String query, Pageable pageable) {
        List<String> terms = terms(query);
        if (terms.isEmpty()) {
            throw ApiException.badRequest("Search text must contain at least one letter or digit");
        }
        try {
            if ("like".equals(mode)) {
                return likeSearch(terms, pageable);
            }
            Page<Long> result;
            try {
                result = oracleTextSearch(terms, pageable);
            } catch (PersistenceException ex) {
                // e.g. a very short prefix expanding to too many words: fall back instead of failing.
                log.warn("Oracle Text search failed, using LIKE instead: {}", ex.getMessage());
                return likeSearch(terms, pageable);
            }
            // Nothing starts with the words typed: also look inside words (e.g. "cube" in "flexcube").
            return result.getTotalElements() > 0 ? result : likeSearch(terms, pageable);
        } catch (PersistenceException ex) {
            log.error("Search failed (mode={}): {}", mode, ex.getMessage(), ex);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Search is temporarily unavailable");
        }
    }

    /**
     * Prefix search: every term must match the START of a word ("flex" finds "flexcube", "test" finds "testing").
     * Each character is escaped with a backslash so words like OR / AND / NEAR are never read as operators,
     * then the % wildcard is added.
     */
    static String containsQuery(List<String> terms) {
        StringBuilder contains = new StringBuilder();
        for (String term : terms) {
            if (contains.length() > 0) {
                contains.append(" AND ");
            }
            contains.append('(');
            for (char ch : term.toCharArray()) {
                contains.append('\\').append(ch);
            }
            contains.append("%)");
        }
        return contains.toString();
    }

    private Page<Long> oracleTextSearch(List<String> terms, Pageable pageable) {
        String contains = containsQuery(terms);

        Query count = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM CU_KNOWLEDGE k WHERE k.STATUS = 'PUBLISHED' AND CONTAINS(k.SEARCH_TEXT, :q) > 0");
        count.setParameter("q", contains);
        long total = ((Number) count.getSingleResult()).longValue();
        if (total == 0) {
            return new PageImpl<>(new ArrayList<Long>(), pageable, 0);
        }

        Query select = entityManager.createNativeQuery(
                "SELECT k.KNOWLEDGE_ID FROM CU_KNOWLEDGE k "
                        + "WHERE k.STATUS = 'PUBLISHED' AND CONTAINS(k.SEARCH_TEXT, :q, 1) > 0 "
                        + "ORDER BY SCORE(1) DESC, k.KNOWLEDGE_ID DESC "
                        + "OFFSET :off ROWS FETCH NEXT :lim ROWS ONLY");
        select.setParameter("q", contains);
        select.setParameter("off", pageable.getOffset());
        select.setParameter("lim", pageable.getPageSize());
        return new PageImpl<>(toIds(select.getResultList()), pageable, total);
    }

    private Page<Long> likeSearch(List<String> terms, Pageable pageable) {
        StringBuilder where = new StringBuilder("WHERE k.STATUS = 'PUBLISHED'");
        for (int i = 0; i < terms.size(); i++) {
            where.append(" AND LOWER(k.SEARCH_TEXT) LIKE :t").append(i).append(" ESCAPE '\\'");
        }

        Query count = entityManager.createNativeQuery("SELECT COUNT(*) FROM CU_KNOWLEDGE k " + where);
        Query select = entityManager.createNativeQuery("SELECT k.KNOWLEDGE_ID FROM CU_KNOWLEDGE k " + where
                + " ORDER BY k.PUBLISHED_AT DESC, k.KNOWLEDGE_ID DESC OFFSET :off ROWS FETCH NEXT :lim ROWS ONLY");
        for (int i = 0; i < terms.size(); i++) {
            String pattern = "%" + escapeLike(terms.get(i).toLowerCase(Locale.ROOT)) + "%";
            count.setParameter("t" + i, pattern);
            select.setParameter("t" + i, pattern);
        }
        long total = ((Number) count.getSingleResult()).longValue();
        if (total == 0) {
            return new PageImpl<>(new ArrayList<Long>(), pageable, 0);
        }
        select.setParameter("off", pageable.getOffset());
        select.setParameter("lim", pageable.getPageSize());
        return new PageImpl<>(toIds(select.getResultList()), pageable, total);
    }

    /** Splits on whitespace, strips Oracle Text escape characters, keeps terms containing a letter or digit. */
    static List<String> terms(String query) {
        List<String> terms = new ArrayList<>();
        if (query == null) {
            return terms;
        }
        for (String raw : query.trim().split("\\s+")) {
            String term = raw.replaceAll("[{}\\\\]", "").trim();
            if (term.length() > MAX_TERM_LENGTH) {
                term = term.substring(0, MAX_TERM_LENGTH);
            }
            if (term.matches(".*[\\p{L}\\p{N}].*") && !terms.contains(term)) {
                terms.add(term);
            }
            if (terms.size() == MAX_TERMS) {
                break;
            }
        }
        return terms;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static List<Long> toIds(List<?> rows) {
        List<Long> ids = new ArrayList<>(rows.size());
        for (Object row : rows) {
            ids.add(((Number) row).longValue());
        }
        return ids;
    }
}
