package com.example.cube.knowledge.controller;

import com.example.cube.common.response.PageResponse;
import com.example.cube.knowledge.dto.AttachmentResponse;
import com.example.cube.knowledge.dto.KnowledgeDetailResponse;
import com.example.cube.knowledge.dto.KnowledgeSummaryResponse;
import com.example.cube.knowledge.dto.RejectRequest;
import com.example.cube.knowledge.dto.UpdateKnowledgeRequest;
import com.example.cube.knowledge.service.AttachmentService;
import com.example.cube.knowledge.service.KnowledgeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final AttachmentService attachmentService;

    public KnowledgeController(KnowledgeService knowledgeService, AttachmentService attachmentService) {
        this.knowledgeService = knowledgeService;
        this.attachmentService = attachmentService;
    }

    /**
     * multipart/form-data fields (lowercase): title, description, keywords (repeat), files (repeat).
     * Employees -> PENDING; approvers -> PUBLISHED. Pass draft=true to save as DRAFT instead.
     */
    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KnowledgeDetailResponse> submit(
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "keywords", required = false) List<String> keywords,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "draft", defaultValue = "false") boolean draft) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(knowledgeService.create(title, description, keywords, files, draft));
    }

    /** Same fields as /submit; always saves as DRAFT. */
    @PostMapping(value = "/draft", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KnowledgeDetailResponse> saveDraft(
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "keywords", required = false) List<String> keywords,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(knowledgeService.create(title, description, keywords, files, true));
    }

    @GetMapping("/published")
    public PageResponse<KnowledgeSummaryResponse> published(@RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size) {
        return knowledgeService.listPublished(page, size);
    }

    @GetMapping("/mine")
    public PageResponse<KnowledgeSummaryResponse> mine(@RequestParam(required = false) String status,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        return knowledgeService.listMine(status, page, size);
    }

    /** Approval page tabs: ?status=PENDING | PUBLISHED | REJECTED | ARCHIVED | DRAFT */
    @GetMapping
    public PageResponse<KnowledgeSummaryResponse> byStatus(@RequestParam(required = false) String status,
                                                           @RequestParam(required = false) Integer page,
                                                           @RequestParam(required = false) Integer size) {
        return knowledgeService.listByStatus(status, page, size);
    }

    /** Items the current user bookmarked (published only). */
    @GetMapping("/bookmarks")
    public PageResponse<KnowledgeSummaryResponse> bookmarks(@RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size) {
        return knowledgeService.listBookmarks(page, size);
    }

    @PutMapping("/{id}/bookmark")
    public ResponseEntity<Void> bookmark(@PathVariable Long id) {
        knowledgeService.bookmark(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/bookmark")
    public ResponseEntity<Void> removeBookmark(@PathVariable Long id) {
        knowledgeService.removeBookmark(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public PageResponse<KnowledgeSummaryResponse> search(@RequestParam(value = "q", required = false) String q,
                                                         @RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer size) {
        return knowledgeService.search(q, page, size);
    }

    @GetMapping("/{id}")
    public KnowledgeDetailResponse get(@PathVariable Long id) {
        return knowledgeService.get(id);
    }

    @PutMapping("/{id}")
    public KnowledgeDetailResponse update(@PathVariable Long id, @RequestBody UpdateKnowledgeRequest request) {
        return knowledgeService.update(id, request);
    }

    /** Owner submits a DRAFT or resubmits a REJECTED item. */
    @PostMapping("/{id}/submit")
    public KnowledgeDetailResponse submitExisting(@PathVariable Long id) {
        return knowledgeService.submit(id);
    }

    @PostMapping("/{id}/approve")
    public KnowledgeDetailResponse approve(@PathVariable Long id) {
        return knowledgeService.approve(id);
    }

    @PostMapping("/{id}/reject")
    public KnowledgeDetailResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        return knowledgeService.reject(id, request.getReason());
    }

    @PostMapping("/{id}/archive")
    public KnowledgeDetailResponse archive(@PathVariable Long id) {
        return knowledgeService.archive(id);
    }

    @PostMapping("/{id}/restore")
    public KnowledgeDetailResponse restore(@PathVariable Long id) {
        return knowledgeService.restore(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/attachments")
    public List<AttachmentResponse> attachments(@PathVariable Long id) {
        return attachmentService.list(id);
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<AttachmentResponse>> addAttachments(
            @PathVariable Long id,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attachmentService.addAttachments(id, files));
    }
}
