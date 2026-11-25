package com.example.cube.knowledge.repository;

import com.example.cube.knowledge.entity.KnowledgeKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface KnowledgeKeywordRepository extends JpaRepository<KnowledgeKeyword, KnowledgeKeyword.Id> {

    List<KnowledgeKeyword> findByIdKnowledgeId(Long knowledgeId);

    @Query("select k.keywordName from KnowledgeKeyword kk, Keyword k "
            + "where k.keywordId = kk.id.keywordId and kk.id.knowledgeId = :id order by lower(k.keywordName)")
    List<String> findKeywordNames(@Param("id") Long knowledgeId);

    /** Rows of [knowledgeId, keywordName]. */
    @Query("select kk.id.knowledgeId, k.keywordName from KnowledgeKeyword kk, Keyword k "
            + "where k.keywordId = kk.id.keywordId and kk.id.knowledgeId in :ids order by lower(k.keywordName)")
    List<Object[]> findKeywordNames(@Param("ids") Collection<Long> knowledgeIds);
}
