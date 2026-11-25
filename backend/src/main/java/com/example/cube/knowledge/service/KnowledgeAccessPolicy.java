package com.example.cube.knowledge.service;

import com.example.cube.auth.Permissions;
import com.example.cube.knowledge.dto.KnowledgeActions;
import com.example.cube.knowledge.entity.Knowledge;
import com.example.cube.knowledge.entity.KnowledgeStatus;
import com.example.cube.security.CurrentUser;
import org.springframework.stereotype.Component;

/**
 * Every knowledge/attachment authorization rule lives here so the services and the
 * "actions" block returned to the UI can never disagree.
 *
 * Reviewer = holds KNOWLEDGE:APPROVE or KNOWLEDGE:EDIT (managers).
 */
@Component
public class KnowledgeAccessPolicy {

    public boolean isOwner(CurrentUser user, Knowledge k) {
        return k.getOwnerId() != null && k.getOwnerId().equals(user.getUserId());
    }

    public boolean isReviewer(CurrentUser user) {
        return user.has(Permissions.KNOWLEDGE_APPROVE) || user.has(Permissions.KNOWLEDGE_EDIT);
    }

    /**
     * Published: anyone with VIEW (and reviewers).
     * Pending: the author and the assigned reviewer only; if unassigned, any reviewer.
     * Anything else: the author or a reviewer.
     * Requires {@link ReviewRouting#resolve} to have been called on the item.
     */
    public boolean canView(CurrentUser user, Knowledge k) {
        if (isOwner(user, k)) {
            return true;
        }
        if (k.getStatus() == KnowledgeStatus.PUBLISHED) {
            return user.has(Permissions.KNOWLEDGE_VIEW) || isReviewer(user);
        }
        if (k.getStatus() == KnowledgeStatus.PENDING) {
            return isAssignedOrOpen(user, k) && isReviewer(user);
        }
        return isReviewer(user);
    }

    /** True if the item is assigned to this user, or not assigned to anyone who can review it. */
    public boolean isAssignedOrOpen(CurrentUser user, Knowledge k) {
        Long reviewer = k.getEffectiveReviewerId();
        return reviewer == null || reviewer.equals(user.getUserId());
    }

    /**
     * Download / preview. The owner and reviewers can always download what they can see
     * (a manager must be able to open an employee's pending attachment to review it);
     * everyone else needs KNOWLEDGE:DOWNLOAD.
     */
    public boolean canDownload(CurrentUser user, Knowledge k) {
        if (!canView(user, k)) {
            return false;
        }
        return isOwner(user, k) || isReviewer(user) || user.has(Permissions.KNOWLEDGE_DOWNLOAD);
    }

    /** Managers (EDIT) edit anything; owners edit their own DRAFT or REJECTED items (resubmit flow). */
    public boolean canEdit(CurrentUser user, Knowledge k) {
        if (user.has(Permissions.KNOWLEDGE_EDIT)) {
            return true;
        }
        return isOwner(user, k) && isOwnerEditable(k.getStatus());
    }

    public boolean canSubmit(CurrentUser user, Knowledge k) {
        return isOwner(user, k) && user.has(Permissions.KNOWLEDGE_SUBMIT) && isOwnerEditable(k.getStatus());
    }

    /** Approve / reject: only the assigned reviewer (or any approver if the item is unassigned). */
    public boolean canReview(CurrentUser user, Knowledge k) {
        return user.has(Permissions.KNOWLEDGE_APPROVE)
                && k.getStatus() == KnowledgeStatus.PENDING
                && isAssignedOrOpen(user, k);
    }

    public boolean canArchive(CurrentUser user, Knowledge k) {
        return user.has(Permissions.KNOWLEDGE_APPROVE) && k.getStatus() == KnowledgeStatus.PUBLISHED;
    }

    public boolean canRestore(CurrentUser user, Knowledge k) {
        return user.has(Permissions.KNOWLEDGE_APPROVE) && k.getStatus() == KnowledgeStatus.ARCHIVED;
    }

    /** Managers (DELETE) delete anything; owners may discard their own drafts. */
    public boolean canDelete(CurrentUser user, Knowledge k) {
        if (user.has(Permissions.KNOWLEDGE_DELETE)) {
            return true;
        }
        return isOwner(user, k) && k.getStatus() == KnowledgeStatus.DRAFT;
    }

    /** Add / remove attachments: reviewers any time; owners until the item is published. */
    public boolean canManageAttachments(CurrentUser user, Knowledge k) {
        if (isReviewer(user)) {
            return true;
        }
        KnowledgeStatus s = k.getStatus();
        return isOwner(user, k)
                && (s == KnowledgeStatus.DRAFT || s == KnowledgeStatus.PENDING || s == KnowledgeStatus.REJECTED);
    }

    public KnowledgeActions actions(CurrentUser user, Knowledge k) {
        KnowledgeActions a = new KnowledgeActions();
        a.setCanEdit(canEdit(user, k));
        a.setCanSubmit(canSubmit(user, k));
        a.setCanApprove(canReview(user, k));
        a.setCanReject(canReview(user, k));
        a.setCanArchive(canArchive(user, k));
        a.setCanRestore(canRestore(user, k));
        a.setCanDelete(canDelete(user, k));
        a.setCanDownload(canDownload(user, k));
        a.setCanManageAttachments(canManageAttachments(user, k));
        return a;
    }

    private static boolean isOwnerEditable(KnowledgeStatus status) {
        return status == KnowledgeStatus.DRAFT || status == KnowledgeStatus.REJECTED;
    }
}
