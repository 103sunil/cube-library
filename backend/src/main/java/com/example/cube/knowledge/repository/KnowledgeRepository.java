package com.example.cube.knowledge.repository;

import com.example.cube.knowledge.entity.Knowledge;
import com.example.cube.knowledge.entity.KnowledgeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface KnowledgeRepository extends JpaRepository<Knowledge, Long> {

    Page<Knowledge> findByStatus(KnowledgeStatus status, Pageable pageable);

    Page<Knowledge> findByOwnerId(Long ownerId, Pageable pageable);

    Page<Knowledge> findByOwnerIdAndStatus(Long ownerId, KnowledgeStatus status, Pageable pageable);

    /**
     * Pending items a reviewer should see: assigned to them, unassigned, or assigned to someone
     * who can no longer approve (not in {@code approvers}).
     */
    @Query("select k from Knowledge k where k.status = com.example.cube.knowledge.entity.KnowledgeStatus.PENDING "
            + "and (k.assignedReviewerId = :me or k.assignedReviewerId is null or k.assignedReviewerId not in :approvers)")
    Page<Knowledge> findPendingQueue(@Param("me") Long me, @Param("approvers") Collection<Long> approvers, Pageable pageable);

    /** Moves an author's waiting submissions to a new reviewer (null = shared queue). */
    @Modifying
    @Query("update Knowledge k set k.assignedReviewerId = :reviewer "
            + "where k.ownerId = :owner and k.status = com.example.cube.knowledge.entity.KnowledgeStatus.PENDING")
    int reassignPending(@Param("owner") Long ownerId, @Param("reviewer") Long reviewerId);

    /** Hands waiting items of a removed reviewer to another reviewer (null = shared queue). */
    @Modifying
    @Query("update Knowledge k set k.assignedReviewerId = :to "
            + "where k.assignedReviewerId = :from and k.status = com.example.cube.knowledge.entity.KnowledgeStatus.PENDING")
    int moveReviewer(@Param("from") Long fromReviewerId, @Param("to") Long toReviewerId);
}
