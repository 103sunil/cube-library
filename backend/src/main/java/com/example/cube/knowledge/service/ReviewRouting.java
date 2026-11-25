package com.example.cube.knowledge.service;

import com.example.cube.auth.Permissions;
import com.example.cube.auth.entity.User;
import com.example.cube.auth.repository.UserRepository;
import com.example.cube.auth.service.PermissionService;
import com.example.cube.knowledge.entity.Knowledge;
import com.example.cube.knowledge.entity.KnowledgeStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sends each submission up the reporting line to the nearest person who can approve.
 *
 *  - On (re)submit the first active approver (KNOWLEDGE:APPROVE) found by walking up from the author's
 *    REPORTING_MANAGER_ID is stored as ASSIGNED_REVIEWER_ID. Example: employee -> team lead (no approve
 *    right, skipped) -> manager: the manager reviews.
 *  - Only the assigned reviewer can see and approve/reject the pending item.
 *  - Items without a usable reviewer (no manager set, or the manager was deactivated or lost
 *    approve rights since) are "unassigned": any approver can pick them up, so nothing gets stuck.
 */
@Component
public class ReviewRouting {

    private final PermissionService permissionService;
    private final UserRepository userRepository;

    public ReviewRouting(PermissionService permissionService, UserRepository userRepository) {
        this.permissionService = permissionService;
        this.userRepository = userRepository;
    }

    /** Active users who can approve right now. */
    public Set<Long> availableApprovers() {
        return permissionService.usersWithPermission(Permissions.KNOWLEDGE_APPROVE);
    }

    /** The reviewer for a new submission by this author, or null for the shared queue. */
    public Long reviewerFor(Long authorId) {
        User author = userRepository.findById(authorId).orElse(null);
        Long manager = author == null ? null : author.getReportingManagerId();
        if (manager == null || manager.equals(authorId)) {
            return null;
        }
        Long reviewer = firstApproverFrom(manager);
        return authorId.equals(reviewer) ? null : reviewer;
    }

    /** userId itself if it can approve, otherwise the nearest approver above it (null if none). */
    public Long firstApproverFrom(Long userId) {
        Set<Long> approvers = availableApprovers();
        Set<Long> seen = new HashSet<>();
        Long current = userId;
        while (current != null && seen.add(current)) {
            if (approvers.contains(current)) {
                return current;
            }
            current = userRepository.findById(current).map(User::getReportingManagerId).orElse(null);
        }
        return null;
    }

    /** Everyone below this person in the reporting line (direct and indirect), top-down. */
    public List<User> everyoneBelow(Long managerId) {
        List<User> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        seen.add(managerId);
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(managerId);
        while (!queue.isEmpty()) {
            for (User u : userRepository.findByReportingManagerIdOrderByUsernameAsc(queue.poll())) {
                if (seen.add(u.getUserId())) {
                    result.add(u);
                    queue.add(u.getUserId());
                }
            }
        }
        return result;
    }

    /** True if candidate is the person or somewhere below them (so it can't become their manager). */
    public boolean isSelfOrBelow(Long personId, Long candidateId) {
        Set<Long> seen = new HashSet<>();
        Long current = candidateId;
        while (current != null && seen.add(current)) {
            if (current.equals(personId)) {
                return true;
            }
            current = userRepository.findById(current).map(User::getReportingManagerId).orElse(null);
        }
        return false;
    }

    /** Fills in who may review the item now (null = any approver). Call before access checks. */
    public Knowledge resolve(Knowledge k) {
        k.setEffectiveReviewerId(effectiveReviewer(k, null));
        return k;
    }

    /** Same as {@link #resolve} but reuses an already-loaded approver set (for lists). */
    public Long effectiveReviewer(Knowledge k, Set<Long> approvers) {
        if (k.getStatus() != KnowledgeStatus.PENDING || k.getAssignedReviewerId() == null) {
            return null;
        }
        Set<Long> available = approvers != null ? approvers : availableApprovers();
        return available.contains(k.getAssignedReviewerId()) ? k.getAssignedReviewerId() : null;
    }
}
