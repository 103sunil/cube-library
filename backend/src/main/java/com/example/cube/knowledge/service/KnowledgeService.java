package com.example.cube.knowledge.service;

import com.example.cube.audit.AuditService;
import com.example.cube.auth.Permissions;
import com.example.cube.auth.entity.User;
import com.example.cube.auth.repository.UserRepository;
import com.example.cube.common.exception.ApiException;
import com.example.cube.common.response.PageResponse;
import com.example.cube.knowledge.dto.KnowledgeDetailResponse;
import com.example.cube.knowledge.dto.KnowledgeSummaryResponse;
import com.example.cube.knowledge.dto.UpdateKnowledgeRequest;
import com.example.cube.knowledge.entity.Attachment;
import com.example.cube.knowledge.entity.Knowledge;
import com.example.cube.knowledge.entity.KnowledgeKeyword;
import com.example.cube.knowledge.entity.KnowledgeStatus;
import com.example.cube.knowledge.repository.AttachmentRepository;
import com.example.cube.knowledge.repository.BookmarkRepository;
import com.example.cube.knowledge.entity.Bookmark;
import com.example.cube.knowledge.entity.Keyword;
import com.example.cube.knowledge.repository.KeywordRepository;
import com.example.cube.knowledge.repository.KnowledgeKeywordRepository;
import com.example.cube.knowledge.repository.KnowledgeRepository;
import com.example.cube.knowledge.repository.KnowledgeSearchRepository;
import com.example.cube.security.CurrentUser;
import com.example.cube.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class KnowledgeService {

    static final int TITLE_MAX = 255;
    static final int DESCRIPTION_MAX = 200_000;
    static final int KEYWORD_MAX_LENGTH = 50;
    static final int KEYWORDS_MAX = 20;
    static final int SUMMARY_LENGTH = 220;

    private static final Sort NEWEST_UPDATED = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("knowledgeId"));
    private static final Sort NEWEST_PUBLISHED = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("knowledgeId"));

    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeKeywordRepository linkRepository;
    private final KeywordRepository keywordRepository;
    private final AttachmentRepository attachmentRepository;
    private final KnowledgeSearchRepository searchRepository;
    private final UserRepository userRepository;
    private final AttachmentService attachmentService;
    private final KnowledgeAccessPolicy policy;
    private final ReviewRouting routing;
    private final BookmarkRepository bookmarkRepository;
    private final AuditService auditService;

    public KnowledgeService(KnowledgeRepository knowledgeRepository,
                            KnowledgeKeywordRepository linkRepository,
                            KeywordRepository keywordRepository,
                            AttachmentRepository attachmentRepository,
                            KnowledgeSearchRepository searchRepository,
                            UserRepository userRepository,
                            AttachmentService attachmentService,
                            KnowledgeAccessPolicy policy,
                            ReviewRouting routing,
                            BookmarkRepository bookmarkRepository,
                            AuditService auditService) {
        this.routing = routing;
        this.bookmarkRepository = bookmarkRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.linkRepository = linkRepository;
        this.keywordRepository = keywordRepository;
        this.attachmentRepository = attachmentRepository;
        this.searchRepository = searchRepository;
        this.userRepository = userRepository;
        this.attachmentService = attachmentService;
        this.policy = policy;
        this.auditService = auditService;
    }

    // ================================================================== create

    /**
     * Creates an item with keywords and attachments in one request.
     * draft=true  -> DRAFT (description optional).
     * otherwise   -> PENDING, or PUBLISHED directly when the submitter holds KNOWLEDGE:APPROVE.
     */
    public KnowledgeDetailResponse create(String title, String description, List<String> keywords,
                                          List<MultipartFile> files, boolean draft) {
        CurrentUser me = SecurityUtils.require(Permissions.KNOWLEDGE_SUBMIT);

        String cleanTitle = cleanTitle(title);
        String cleanDescription = cleanDescription(description, !draft);
        List<String> cleanKeywords = cleanKeywords(keywords);
        List<MultipartFile> uploads = attachmentService.validateUploads(files);
        if (!draft) {
            requireKeywords(cleanKeywords.size());
            requireAttachments(uploads.size());
        }

        LocalDateTime now = LocalDateTime.now();
        Knowledge k = new Knowledge();
        k.setTitle(cleanTitle);
        k.setDescription(cleanDescription);
        k.setOwnerId(me.getUserId());
        k.setCreatedAt(now);
        k.setUpdatedAt(now);
        if (draft) {
            k.setStatus(KnowledgeStatus.DRAFT);
        } else {
            applySubmission(k, me, now);
        }
        k.setSearchText(buildSearchText(cleanTitle, cleanDescription, cleanKeywords));
        k = knowledgeRepository.save(k);

        saveKeywords(k.getKnowledgeId(), cleanKeywords);
        attachmentService.storeUploads(k, uploads, me);

        auditService.record(draft ? "KNOWLEDGE_DRAFT" : "KNOWLEDGE_SUBMIT", "KNOWLEDGE", k.getKnowledgeId(),
                "status=" + k.getStatus() + ", files=" + uploads.size());
        return toDetail(k, me);
    }

    // ================================================================== read

    @Transactional(readOnly = true)
    public KnowledgeDetailResponse get(Long id) {
        CurrentUser me = SecurityUtils.currentUser();
        return toDetail(loadVisible(id, me), me);
    }

    @Transactional(readOnly = true)
    public PageResponse<KnowledgeSummaryResponse> listPublished(Integer page, Integer size) {
        SecurityUtils.require(Permissions.KNOWLEDGE_VIEW);
        Page<Knowledge> result = knowledgeRepository.findByStatus(KnowledgeStatus.PUBLISHED,
                PageResponse.pageable(page, size, NEWEST_PUBLISHED));
        return PageResponse.of(result, toSummaries(result.getContent()));
    }

    /** The caller's own items, optionally filtered by status. */
    @Transactional(readOnly = true)
    public PageResponse<KnowledgeSummaryResponse> listMine(String status, Integer page, Integer size) {
        CurrentUser me = SecurityUtils.currentUser();
        Pageable pageable = PageResponse.pageable(page, size, NEWEST_UPDATED);
        Page<Knowledge> result = isBlank(status)
                ? knowledgeRepository.findByOwnerId(me.getUserId(), pageable)
                : knowledgeRepository.findByOwnerIdAndStatus(me.getUserId(), parseStatus(status), pageable);
        return PageResponse.of(result, toSummaries(result.getContent()));
    }

    /** The caller's bookmarked items that are still published. */
    @Transactional(readOnly = true)
    public PageResponse<KnowledgeSummaryResponse> listBookmarks(Integer page, Integer size) {
        CurrentUser me = SecurityUtils.require(Permissions.KNOWLEDGE_VIEW);
        Page<Knowledge> result = bookmarkRepository.findBookmarked(me.getUserId(),
                PageResponse.pageable(page, size, Sort.unsorted()));
        return PageResponse.of(result, toSummaries(result.getContent()));
    }

    /** Bookmarks a published item for the caller (idempotent). */
    public void bookmark(Long id) {
        CurrentUser me = SecurityUtils.require(Permissions.KNOWLEDGE_VIEW);
        Knowledge k = loadVisible(id, me);
        if (k.getStatus() != KnowledgeStatus.PUBLISHED) {
            throw ApiException.conflict("Only published items can be bookmarked");
        }
        Bookmark.Id key = new Bookmark.Id(me.getUserId(), id);
        if (!bookmarkRepository.existsById(key)) {
            bookmarkRepository.save(new Bookmark(me.getUserId(), id));
        }
    }

    /** Removes the caller's bookmark (idempotent). */
    public void removeBookmark(Long id) {
        CurrentUser me = SecurityUtils.currentUser();
        Bookmark.Id key = new Bookmark.Id(me.getUserId(), id);
        if (bookmarkRepository.existsById(key)) {
            bookmarkRepository.deleteById(key);
        }
    }

    /** Review queue / tabs: any status for reviewers; PUBLISHED for anyone with VIEW. */
    @Transactional(readOnly = true)
    public PageResponse<KnowledgeSummaryResponse> listByStatus(String status, Integer page, Integer size) {
        if (isBlank(status)) {
            throw ApiException.badRequest("Query parameter 'status' is required. Allowed: "
                    + Arrays.toString(KnowledgeStatus.values()));
        }
        KnowledgeStatus parsed = parseStatus(status);
        if (parsed == KnowledgeStatus.PUBLISHED) {
            return listPublished(page, size);
        }
        CurrentUser me = SecurityUtils.currentUser();
        if (!policy.isReviewer(me)) {
            throw ApiException.forbidden("Only reviewers can list " + parsed + " items");
        }
        Pageable pageable = PageResponse.pageable(page, size, NEWEST_UPDATED);
        Page<Knowledge> result;
        if (parsed == KnowledgeStatus.PENDING) {
            // Only items assigned to me, or not assigned to anyone who can still review them.
            Set<Long> approvers = new LinkedHashSet<>(routing.availableApprovers());
            approvers.add(-1L); // keeps the NOT IN list non-empty
            result = knowledgeRepository.findPendingQueue(me.getUserId(), approvers, pageable);
        } else {
            result = knowledgeRepository.findByStatus(parsed, pageable);
        }
        return PageResponse.of(result, toSummaries(result.getContent()));
    }

    @Transactional(readOnly = true)
    public PageResponse<KnowledgeSummaryResponse> search(String query, Integer page, Integer size) {
        SecurityUtils.require(Permissions.KNOWLEDGE_SEARCH);
        if (isBlank(query)) {
            throw ApiException.badRequest("Query parameter 'q' is required");
        }
        Page<Long> ids = searchRepository.searchPublishedIds(query, PageResponse.pageable(page, size, Sort.unsorted()));

        Map<Long, Knowledge> byId = new HashMap<>();
        for (Knowledge k : knowledgeRepository.findAllById(ids.getContent())) {
            byId.put(k.getKnowledgeId(), k);
        }
        List<Knowledge> ordered = new ArrayList<>();
        for (Long id : ids.getContent()) {
            Knowledge k = byId.get(id);
            if (k != null) {
                ordered.add(k);
            }
        }
        return PageResponse.of(ids, toSummaries(ordered));
    }

    // ================================================================== update / workflow

    /** Edits title, description and keywords. Status is unchanged; owners then call submit to resubmit. */
    public KnowledgeDetailResponse update(Long id, UpdateKnowledgeRequest request) {
        CurrentUser me = SecurityUtils.currentUser();
        Knowledge k = loadVisible(id, me);
        if (!policy.canEdit(me, k)) {
            throw ApiException.forbidden(policy.isOwner(me, k)
                    ? "You can only edit your item while it is DRAFT or REJECTED (current: " + k.getStatus() + ")"
                    : "You do not have permission to edit this item");
        }
        String title = cleanTitle(request.getTitle());
        String description = cleanDescription(request.getDescription(), k.getStatus() != KnowledgeStatus.DRAFT);
        List<String> keywords = cleanKeywords(request.getKeywords());
        if (k.getStatus() != KnowledgeStatus.DRAFT) {
            requireKeywords(keywords.size());
        }

        k.setTitle(title);
        k.setDescription(description);
        k.setSearchText(buildSearchText(title, description, keywords));
        k.setUpdatedAt(LocalDateTime.now());
        replaceKeywords(k.getKnowledgeId(), keywords);

        auditService.record("KNOWLEDGE_UPDATE", "KNOWLEDGE", k.getKnowledgeId(), "status=" + k.getStatus());
        return toDetail(k, me);
    }

    /** Owner submits a DRAFT, or resubmits a REJECTED item after editing it. */
    public KnowledgeDetailResponse submit(Long id) {
        CurrentUser me = SecurityUtils.currentUser();
        Knowledge k = loadVisible(id, me);
        if (!policy.isOwner(me, k)) {
            throw ApiException.forbidden("Only the author can submit this item");
        }
        if (!me.has(Permissions.KNOWLEDGE_SUBMIT)) {
            throw ApiException.forbidden("You do not have permission to submit knowledge");
        }
        KnowledgeStatus previous = k.getStatus();
        if (previous != KnowledgeStatus.DRAFT && previous != KnowledgeStatus.REJECTED) {
            throw ApiException.conflict("Only DRAFT or REJECTED items can be submitted (current: " + previous + ")");
        }
        cleanDescription(k.getDescription(), true);
        requireKeywords(linkRepository.findKeywordNames(k.getKnowledgeId()).size());
        requireAttachments(attachmentRepository.countByKnowledgeId(k.getKnowledgeId()));

        LocalDateTime now = LocalDateTime.now();
        applySubmission(k, me, now);
        k.setUpdatedAt(now);
        auditService.record(previous == KnowledgeStatus.REJECTED ? "KNOWLEDGE_RESUBMIT" : "KNOWLEDGE_SUBMIT",
                "KNOWLEDGE", k.getKnowledgeId(), "status=" + k.getStatus());
        return toDetail(k, me);
    }

    public KnowledgeDetailResponse approve(Long id) {
        CurrentUser me = SecurityUtils.require(Permissions.KNOWLEDGE_APPROVE);
        Knowledge k = loadVisible(id, me);
        requireStatus(k, KnowledgeStatus.PENDING, "approved");
        requireAssignedReviewer(k, me);

        LocalDateTime now = LocalDateTime.now();
        k.setStatus(KnowledgeStatus.PUBLISHED);
        k.setRejectReason(null);
        k.setReviewedBy(me.getUserId());
        k.setReviewedAt(now);
        k.setPublishedAt(now);
        k.setUpdatedAt(now);
        auditService.record("KNOWLEDGE_APPROVE", "KNOWLEDGE", k.getKnowledgeId(), null);
        return toDetail(k, me);
    }

    public KnowledgeDetailResponse reject(Long id, String reason) {
        CurrentUser me = SecurityUtils.require(Permissions.KNOWLEDGE_APPROVE);
        Knowledge k = loadVisible(id, me);
        requireStatus(k, KnowledgeStatus.PENDING, "rejected");
        requireAssignedReviewer(k, me);
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isEmpty()) {
            throw ApiException.badRequest("A rejection reason is required");
        }

        LocalDateTime now = LocalDateTime.now();
        k.setStatus(KnowledgeStatus.REJECTED);
        k.setRejectReason(cleanReason);
        k.setReviewedBy(me.getUserId());
        k.setReviewedAt(now);
        k.setUpdatedAt(now);
        auditService.record("KNOWLEDGE_REJECT", "KNOWLEDGE", k.getKnowledgeId(), cleanReason);
        return toDetail(k, me);
    }

    public KnowledgeDetailResponse archive(Long id) {
        CurrentUser me = SecurityUtils.require(Permissions.KNOWLEDGE_APPROVE);
        Knowledge k = loadVisible(id, me);
        requireStatus(k, KnowledgeStatus.PUBLISHED, "archived");
        k.setStatus(KnowledgeStatus.ARCHIVED);
        k.setUpdatedAt(LocalDateTime.now());
        auditService.record("KNOWLEDGE_ARCHIVE", "KNOWLEDGE", k.getKnowledgeId(), null);
        return toDetail(k, me);
    }

    public KnowledgeDetailResponse restore(Long id) {
        CurrentUser me = SecurityUtils.require(Permissions.KNOWLEDGE_APPROVE);
        Knowledge k = loadVisible(id, me);
        requireStatus(k, KnowledgeStatus.ARCHIVED, "restored");
        k.setStatus(KnowledgeStatus.PUBLISHED);
        k.setUpdatedAt(LocalDateTime.now());
        auditService.record("KNOWLEDGE_RESTORE", "KNOWLEDGE", k.getKnowledgeId(), null);
        return toDetail(k, me);
    }

    /** Deletes the item, its keywords, its attachment rows and (after commit) the stored files. */
    public void delete(Long id) {
        CurrentUser me = SecurityUtils.currentUser();
        Knowledge k = loadVisible(id, me);
        if (!policy.canDelete(me, k)) {
            throw ApiException.forbidden("You do not have permission to delete this item");
        }
        attachmentService.deleteAllFor(k.getKnowledgeId());
        bookmarkRepository.deleteAll(bookmarkRepository.findByIdKnowledgeId(k.getKnowledgeId()));
        linkRepository.deleteAll(linkRepository.findByIdKnowledgeId(k.getKnowledgeId()));
        knowledgeRepository.delete(k);
        auditService.record("KNOWLEDGE_DELETE", "KNOWLEDGE", k.getKnowledgeId(), "title=" + k.getTitle());
    }

    // ================================================================== helpers

    private Knowledge loadVisible(Long id, CurrentUser me) {
        Knowledge k = knowledgeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Knowledge item not found"));
        routing.resolve(k);
        if (!policy.canView(me, k)) {
            // 404 rather than 403 so unpublished items are not revealed.
            throw ApiException.notFound("Knowledge item not found");
        }
        return k;
    }

    private void applySubmission(Knowledge k, CurrentUser me, LocalDateTime now) {
        k.setRejectReason(null);
        if (me.has(Permissions.KNOWLEDGE_APPROVE)) {
            k.setStatus(KnowledgeStatus.PUBLISHED);
            k.setReviewedBy(me.getUserId());
            k.setReviewedAt(now);
            k.setPublishedAt(now);
        } else {
            k.setStatus(KnowledgeStatus.PENDING);
            k.setReviewedBy(null);
            k.setReviewedAt(null);
        }
        // Route to the author's reporting manager (null = shared queue). Approvers publish directly.
        k.setAssignedReviewerId(k.getStatus() == KnowledgeStatus.PENDING ? routing.reviewerFor(k.getOwnerId()) : null);
        routing.resolve(k);
    }

    private void requireAssignedReviewer(Knowledge k, CurrentUser me) {
        if (!policy.isAssignedOrOpen(me, k)) {
            throw ApiException.forbidden("This submission is assigned to another reviewer");
        }
    }

    private static void requireStatus(Knowledge k, KnowledgeStatus expected, String verb) {
        if (k.getStatus() != expected) {
            throw ApiException.conflict("Only " + expected + " items can be " + verb + " (current: " + k.getStatus() + ")");
        }
    }

    /** Links the item to each keyword, reusing an existing CU_KEYWORD row (any letter case) or creating one. */
    private void saveKeywords(Long knowledgeId, List<String> keywords) {
        for (String name : keywords) {
            Keyword keyword = keywordRepository.findByKeywordName(name).orElse(null);
            if (keyword == null) {
                List<Keyword> similar = keywordRepository.findByKeywordNameIgnoreCaseOrderByKeywordIdAsc(name);
                if (!similar.isEmpty()) {
                    keyword = similar.get(0);
                } else {
                    keyword = new Keyword();
                    keyword.setKeywordName(name);
                    keyword = keywordRepository.saveAndFlush(keyword);
                }
            }
            linkRepository.save(new KnowledgeKeyword(keyword.getKeywordId(), knowledgeId));
        }
    }

    private void replaceKeywords(Long knowledgeId, List<String> keywords) {
        linkRepository.deleteAll(linkRepository.findByIdKnowledgeId(knowledgeId));
        linkRepository.flush();
        saveKeywords(knowledgeId, keywords);
    }

    static String cleanTitle(String title) {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) {
            throw ApiException.badRequest("Title is required");
        }
        if (t.length() > TITLE_MAX) {
            throw ApiException.badRequest("Title must be " + TITLE_MAX + " characters or fewer");
        }
        return t;
    }

    static String cleanDescription(String description, boolean required) {
        String d = description == null ? "" : description.trim();
        if (required && d.isEmpty()) {
            throw ApiException.badRequest("Description is required");
        }
        if (d.length() > DESCRIPTION_MAX) {
            throw ApiException.badRequest("Description must be " + DESCRIPTION_MAX + " characters or fewer");
        }
        return d.isEmpty() ? null : d;
    }

    /** Accepts repeated values and/or comma-separated values; trims, collapses spaces, de-duplicates case-insensitively. */
    /** Submitted / published items need at least one keyword (drafts may be incomplete). */
    private static void requireKeywords(int count) {
        if (count == 0) {
            throw ApiException.badRequest("Add at least one keyword");
        }
    }

    /** Submitted / published items need at least one attachment (drafts may be incomplete). */
    private static void requireAttachments(long count) {
        if (count == 0) {
            throw ApiException.badRequest("Attach at least one file");
        }
    }

    static List<String> cleanKeywords(List<String> keywords) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (keywords == null) {
            return result;
        }
        for (String value : keywords) {
            if (value == null) {
                continue;
            }
            for (String part : value.split(",")) {
                String kw = part.trim().replaceAll("\\s+", " ");
                if (kw.isEmpty()) {
                    continue;
                }
                if (kw.length() > KEYWORD_MAX_LENGTH) {
                    throw ApiException.badRequest("Keyword '" + kw.substring(0, 20) + "...' is longer than "
                            + KEYWORD_MAX_LENGTH + " characters");
                }
                if (seen.add(kw.toLowerCase(Locale.ROOT))) {
                    result.add(kw);
                }
            }
        }
        if (result.size() > KEYWORDS_MAX) {
            throw ApiException.badRequest("At most " + KEYWORDS_MAX + " keywords are allowed");
        }
        return result;
    }

    private static String buildSearchText(String title, String description, List<String> keywords) {
        StringBuilder sb = new StringBuilder(title);
        if (description != null) {
            sb.append('\n').append(description);
        }
        for (String kw : keywords) {
            sb.append('\n').append(kw);
        }
        return sb.toString();
    }

    static String summarize(String description) {
        if (description == null) {
            return null;
        }
        String oneLine = description.replaceAll("\\s+", " ").trim();
        if (oneLine.length() <= SUMMARY_LENGTH) {
            return oneLine.isEmpty() ? null : oneLine;
        }
        int cut = oneLine.lastIndexOf(' ', SUMMARY_LENGTH);
        return oneLine.substring(0, cut > SUMMARY_LENGTH / 2 ? cut : SUMMARY_LENGTH) + "…";
    }

    private static KnowledgeStatus parseStatus(String status) {
        try {
            return KnowledgeStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Invalid status '" + status + "'. Allowed: " + Arrays.toString(KnowledgeStatus.values()));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private KnowledgeDetailResponse toDetail(Knowledge k, CurrentUser me) {
        List<String> keywords = linkRepository.findKeywordNames(k.getKnowledgeId());
        List<Attachment> attachments = attachmentRepository.findByKnowledgeIdOrderByAttachmentIdAsc(k.getKnowledgeId());
        User owner = userRepository.findById(k.getOwnerId()).orElse(null);
        User reviewer = k.getReviewedBy() == null ? null : userRepository.findById(k.getReviewedBy()).orElse(null);

        KnowledgeDetailResponse r = new KnowledgeDetailResponse();
        r.setKnowledgeId(k.getKnowledgeId());
        r.setTitle(k.getTitle());
        r.setDescription(k.getDescription());
        r.setStatus(k.getStatus());
        r.setOwnerId(k.getOwnerId());
        r.setOwnerUsername(owner == null ? null : owner.getUsername());
        r.setOwnerName(owner == null ? null : owner.getDisplayName());
        r.setKeywords(keywords);
        r.setAttachments(attachmentService.toResponses(attachments));
        r.setAttachmentCount(attachments.size());
        r.setRejectReason(k.getRejectReason());
        Long assigned = k.getEffectiveReviewerId();
        if (assigned != null) {
            User assignee = userRepository.findById(assigned).orElse(null);
            r.setAssignedReviewerId(assigned);
            r.setAssignedReviewerName(assignee == null ? null : assignee.getDisplayName());
            r.setAssignedToMe(assigned.equals(me.getUserId()));
        }
        r.setReviewedBy(reviewer == null ? null : reviewer.getUsername());
        r.setReviewedAt(k.getReviewedAt());
        r.setPublishedAt(k.getPublishedAt());
        r.setCreatedAt(k.getCreatedAt());
        r.setUpdatedAt(k.getUpdatedAt());
        r.setBookmarked(k.getKnowledgeId() != null && bookmarkRepository.existsById(new Bookmark.Id(me.getUserId(), k.getKnowledgeId())));
        r.setActions(policy.actions(me, k));
        return r;
    }

    /** Batch-loads keywords, attachment counts and owners for a page (no per-row queries). */
    private List<KnowledgeSummaryResponse> toSummaries(List<Knowledge> items) {
        if (items.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        Set<Long> ownerIds = new LinkedHashSet<>();
        for (Knowledge k : items) {
            ids.add(k.getKnowledgeId());
            ownerIds.add(k.getOwnerId());
        }

        Map<Long, List<String>> keywordsById = new HashMap<>();
        for (Object[] row : linkRepository.findKeywordNames(ids)) {
            Long knowledgeId = ((Number) row[0]).longValue();
            List<String> list = keywordsById.get(knowledgeId);
            if (list == null) {
                list = new ArrayList<>();
                keywordsById.put(knowledgeId, list);
            }
            list.add((String) row[1]);
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : attachmentRepository.countByKnowledgeIds(ids)) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        // Effective reviewer for pending items (one approver lookup per page)
        Set<Long> approvers = null;
        Map<Long, Long> reviewerOf = new HashMap<>();
        for (Knowledge k : items) {
            if (k.getStatus() == KnowledgeStatus.PENDING && k.getAssignedReviewerId() != null) {
                if (approvers == null) {
                    approvers = routing.availableApprovers();
                }
                Long reviewer = routing.effectiveReviewer(k, approvers);
                if (reviewer != null) {
                    reviewerOf.put(k.getKnowledgeId(), reviewer);
                    ownerIds.add(reviewer);
                }
            }
        }
        Long myId = SecurityUtils.currentUser().getUserId();
        Set<Long> bookmarked = new HashSet<>(bookmarkRepository.findBookmarkedIds(myId, ids));

        Map<Long, User> owners = new HashMap<>();
        for (User u : userRepository.findAllById(ownerIds)) {
            owners.put(u.getUserId(), u);
        }

        List<KnowledgeSummaryResponse> result = new ArrayList<>();
        for (Knowledge k : items) {
            User owner = owners.get(k.getOwnerId());
            KnowledgeSummaryResponse r = new KnowledgeSummaryResponse();
            r.setKnowledgeId(k.getKnowledgeId());
            r.setTitle(k.getTitle());
            r.setSummary(summarize(k.getDescription()));
            r.setStatus(k.getStatus());
            r.setOwnerId(k.getOwnerId());
            r.setOwnerUsername(owner == null ? null : owner.getUsername());
            r.setOwnerName(owner == null ? null : owner.getDisplayName());
            List<String> kws = keywordsById.get(k.getKnowledgeId());
            r.setKeywords(kws == null ? Collections.<String>emptyList() : kws);
            Long count = counts.get(k.getKnowledgeId());
            r.setAttachmentCount(count == null ? 0 : count);
            r.setRejectReason(k.getRejectReason());
            Long reviewer = reviewerOf.get(k.getKnowledgeId());
            if (reviewer != null) {
                User assignee = owners.get(reviewer);
                r.setAssignedReviewerId(reviewer);
                r.setAssignedReviewerName(assignee == null ? null : assignee.getDisplayName());
                r.setAssignedToMe(reviewer.equals(myId));
            }
            r.setBookmarked(bookmarked.contains(k.getKnowledgeId()));
            r.setCreatedAt(k.getCreatedAt());
            r.setUpdatedAt(k.getUpdatedAt());
            r.setPublishedAt(k.getPublishedAt());
            result.add(r);
        }
        return result;
    }
}
