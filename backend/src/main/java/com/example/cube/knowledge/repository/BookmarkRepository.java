package com.example.cube.knowledge.repository;

import com.example.cube.knowledge.entity.Bookmark;
import com.example.cube.knowledge.entity.Knowledge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BookmarkRepository extends JpaRepository<Bookmark, Bookmark.Id> {

    /** The user's bookmarked items that are still published, newest bookmark first. */
    @Query(value = "select k from Bookmark b, Knowledge k where k.knowledgeId = b.id.knowledgeId and b.id.userId = :user "
            + "and k.status = com.example.cube.knowledge.entity.KnowledgeStatus.PUBLISHED order by b.createdAt desc",
            countQuery = "select count(b) from Bookmark b, Knowledge k where k.knowledgeId = b.id.knowledgeId and b.id.userId = :user "
                    + "and k.status = com.example.cube.knowledge.entity.KnowledgeStatus.PUBLISHED")
    Page<Knowledge> findBookmarked(@Param("user") Long userId, Pageable pageable);

    @Query("select b.id.knowledgeId from Bookmark b where b.id.userId = :user and b.id.knowledgeId in :ids")
    List<Long> findBookmarkedIds(@Param("user") Long userId, @Param("ids") Collection<Long> knowledgeIds);

    List<Bookmark> findByIdKnowledgeId(Long knowledgeId);
}
