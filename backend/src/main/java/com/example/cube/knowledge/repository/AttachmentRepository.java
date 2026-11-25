package com.example.cube.knowledge.repository;

import com.example.cube.knowledge.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByKnowledgeIdOrderByAttachmentIdAsc(Long knowledgeId);

    long countByKnowledgeId(Long knowledgeId);

    @Query("select a.knowledgeId, count(a) from Attachment a where a.knowledgeId in :ids group by a.knowledgeId")
    List<Object[]> countByKnowledgeIds(@Param("ids") Collection<Long> knowledgeIds);
}
