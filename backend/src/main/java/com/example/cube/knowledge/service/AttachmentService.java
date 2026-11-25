package com.example.cube.knowledge.service;

import com.example.cube.audit.AuditService;
import com.example.cube.common.exception.ApiException;
import com.example.cube.knowledge.dto.AttachmentContent;
import com.example.cube.knowledge.dto.AttachmentResponse;
import com.example.cube.knowledge.entity.Attachment;
import com.example.cube.knowledge.entity.Knowledge;
import com.example.cube.knowledge.entity.KnowledgeStatus;
import com.example.cube.knowledge.repository.AttachmentRepository;
import com.example.cube.knowledge.repository.KnowledgeRepository;
import com.example.cube.security.CurrentUser;
import com.example.cube.security.SecurityUtils;
import com.example.cube.storage.FileStorageService;
import com.example.cube.storage.AttachmentFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLConnection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);
    private static final String API_BASE = "/api/v1/attachments/";

    private final AttachmentRepository attachmentRepository;
    private final KnowledgeRepository knowledgeRepository;
    private final FileStorageService storage;
    private final AttachmentFiles files;
    private final KnowledgeAccessPolicy policy;
    private final ReviewRouting routing;
    private final AuditService auditService;
    private final long maxFileSize;
    private final int maxFilesPerRequest;

    public AttachmentService(AttachmentRepository attachmentRepository,
                             KnowledgeRepository knowledgeRepository,
                             FileStorageService storage,
                             AttachmentFiles files,
                             KnowledgeAccessPolicy policy,
                             ReviewRouting routing,
                             AuditService auditService,
                             @Value("${cube.upload.max-file-size-bytes:10485760}") long maxFileSize,
                             @Value("${cube.upload.max-files-per-request:20}") int maxFilesPerRequest) {
        this.attachmentRepository = attachmentRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.storage = storage;
        this.files = files;
        this.routing = routing;
        this.policy = policy;
        this.auditService = auditService;
        this.maxFileSize = maxFileSize;
        this.maxFilesPerRequest = maxFilesPerRequest;
    }

    // ------------------------------------------------------------------ validation / storing

    /**
     * Drops empty "no file selected" parts and enforces the per-file size cap and per-request count.
     */
    public List<MultipartFile> validateUploads(List<MultipartFile> files) {
        List<MultipartFile> uploads = new ArrayList<>();
        if (files == null) {
            return uploads;
        }
        for (MultipartFile file : files) {
            if (file == null) {
                continue;
            }
            String name = file.getOriginalFilename();
            boolean noName = name == null || name.trim().isEmpty();
            if (noName && file.isEmpty()) {
                continue;
            }
            String display = noName ? "(unnamed)" : sanitizeFileName(name);
            if (file.isEmpty()) {
                throw ApiException.badRequest("File '" + display + "' is empty");
            }
            if (file.getSize() > maxFileSize) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "File '" + display + "' is larger than the " + (maxFileSize / (1024 * 1024)) + "MB limit");
            }
            uploads.add(file);
        }
        if (uploads.size() > maxFilesPerRequest) {
            throw ApiException.badRequest("At most " + maxFilesPerRequest + " files can be uploaded at once");
        }
        return uploads;
    }

    /**
     * Stores already-validated uploads for an item. If the surrounding transaction rolls back,
     * the stored files are removed again so no orphans are left in storage.
     */
    public List<Attachment> storeUploads(Knowledge knowledge, List<MultipartFile> uploads, CurrentUser uploader) {
        List<Attachment> saved = new ArrayList<>();
        for (MultipartFile file : uploads) {
            String fileName = sanitizeFileName(file.getOriginalFilename());
            byte[] bytes;
            try {
                bytes = file.getBytes();
            } catch (IOException ex) {
                throw ApiException.badRequest("Could not read uploaded file '" + fileName + "'");
            }
            String contentType = resolveContentType(file.getContentType(), fileName);
            String path = "knowledge/" + knowledge.getKnowledgeId() + "/"
                    + UUID.randomUUID().toString().replace("-", "") + "_" + storageSafeName(fileName);

            String key = storage.store(path, bytes, contentType);
            deleteOnRollback(key);

            Attachment a = new Attachment();
            a.setKnowledgeId(knowledge.getKnowledgeId());
            a.setFileName(fileName);
            a.setContentType(contentType);
            a.setFileSize((long) bytes.length);
            a.setStorageKey(key);
            a.setUploadedBy(uploader.getUserId());
            a.setCreatedAt(LocalDateTime.now());
            saved.add(attachmentRepository.save(a));
        }
        return saved;
    }

    // ------------------------------------------------------------------ endpoints

    public List<AttachmentResponse> addAttachments(Long knowledgeId, List<MultipartFile> files) {
        CurrentUser me = SecurityUtils.currentUser();
        Knowledge k = loadVisibleKnowledge(knowledgeId, me);
        if (!policy.canManageAttachments(me, k)) {
            throw ApiException.forbidden("You cannot add attachments to this item in its current status (" + k.getStatus() + ")");
        }
        List<MultipartFile> uploads = validateUploads(files);
        if (uploads.isEmpty()) {
            throw ApiException.badRequest("No files were provided. Send them as multipart field 'files'.");
        }
        List<Attachment> saved = storeUploads(k, uploads, me);
        k.setUpdatedAt(LocalDateTime.now());
        auditService.record("ATTACHMENT_ADD", "KNOWLEDGE", k.getKnowledgeId(), saved.size() + " file(s)");

        List<AttachmentResponse> result = new ArrayList<>();
        for (Attachment a : saved) {
            result.add(toResponse(a));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(Long knowledgeId) {
        CurrentUser me = SecurityUtils.currentUser();
        Knowledge k = loadVisibleKnowledge(knowledgeId, me);
        return toResponses(attachmentRepository.findByKnowledgeIdOrderByAttachmentIdAsc(k.getKnowledgeId()));
    }

    @Transactional(readOnly = true)
    public AttachmentContent download(Long attachmentId) {
        CurrentUser me = SecurityUtils.currentUser();
        Attachment a = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> ApiException.notFound("Attachment not found"));
        Knowledge k = loadVisibleKnowledge(a.getKnowledgeId(), me);
        if (!policy.canDownload(me, k)) {
            throw ApiException.forbidden("You do not have permission to download attachments");
        }
        byte[] data = files.load(a.getAttachmentId(), a.getStorageKey());
        return new AttachmentContent(a.getFileName(), a.getContentType(), data);
    }

    public void delete(Long attachmentId) {
        CurrentUser me = SecurityUtils.currentUser();
        Attachment a = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> ApiException.notFound("Attachment not found"));
        Knowledge k = loadVisibleKnowledge(a.getKnowledgeId(), me);
        if (!policy.canManageAttachments(me, k)) {
            throw ApiException.forbidden("You cannot remove attachments from this item in its current status (" + k.getStatus() + ")");
        }
        if (k.getStatus() != KnowledgeStatus.DRAFT && attachmentRepository.countByKnowledgeId(k.getKnowledgeId()) <= 1) {
            throw ApiException.badRequest("An item needs at least one attachment. Add the new file first, then remove this one.");
        }
        attachmentRepository.delete(a);
        k.setUpdatedAt(LocalDateTime.now());
        if (files.inActiveStorage(a.getStorageKey())) {
            deleteAfterCommit(Collections.singletonList(a.getStorageKey()));
        }
        auditService.record("ATTACHMENT_DELETE", "ATTACHMENT", a.getAttachmentId(),
                "knowledgeId=" + k.getKnowledgeId() + ", file=" + a.getFileName());
    }

    /** Removes all attachment rows of an item now and their stored files once the transaction commits. */
    public void deleteAllFor(Long knowledgeId) {
        List<Attachment> attachments = attachmentRepository.findByKnowledgeIdOrderByAttachmentIdAsc(knowledgeId);
        List<String> keys = new ArrayList<>();
        for (Attachment a : attachments) {
            // Files outside the active storage (older versions, other disk) are left where they are.
            if (files.inActiveStorage(a.getStorageKey())) {
                keys.add(a.getStorageKey());
            }
        }
        attachmentRepository.deleteAll(attachments);
        deleteAfterCommit(keys);
    }

    // ------------------------------------------------------------------ mapping

    public List<AttachmentResponse> toResponses(List<Attachment> attachments) {
        List<AttachmentResponse> result = new ArrayList<>();
        for (Attachment a : attachments) {
            result.add(toResponse(a));
        }
        return result;
    }

    public AttachmentResponse toResponse(Attachment a) {
        AttachmentResponse r = new AttachmentResponse();
        r.setAttachmentId(a.getAttachmentId());
        r.setKnowledgeId(a.getKnowledgeId());
        r.setFileName(a.getFileName());
        r.setContentType(a.getContentType());
        r.setFileSize(a.getFileSize());
        r.setUploadedBy(a.getUploadedBy());
        r.setCreatedAt(a.getCreatedAt());
        r.setDownloadUrl(API_BASE + a.getAttachmentId() + "/download");
        r.setPreviewUrl(API_BASE + a.getAttachmentId() + "/preview");
        return r;
    }

    // ------------------------------------------------------------------ helpers

    private Knowledge loadVisibleKnowledge(Long knowledgeId, CurrentUser me) {
        Knowledge k = knowledgeRepository.findById(knowledgeId)
                .orElseThrow(() -> ApiException.notFound("Knowledge item not found"));
        routing.resolve(k);
        if (!policy.canView(me, k)) {
            throw ApiException.notFound("Knowledge item not found");
        }
        return k;
    }

    private void deleteOnRollback(final String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(key);
                }
            }
        });
    }

    private void deleteAfterCommit(final List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            for (String key : keys) {
                deleteQuietly(key);
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String key : keys) {
                    deleteQuietly(key);
                }
            }
        });
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Could not delete stored file {}: {}", key, ex.getMessage());
        }
    }

    /** Keeps the user's file name for display; removes path parts and characters unsafe in headers. */
    static String sanitizeFileName(String original) {
        String name = original == null ? "" : original;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}<>:\"|?*]", "").trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            name = "file";
        }
        if (name.length() > 200) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && name.length() - dot <= 20 ? name.substring(dot) : "";
            name = name.substring(0, 200 - ext.length()) + ext;
        }
        return name;
    }

    /** ASCII-only variant used inside the storage path. */
    public static String storageSafeName(String fileName) {
        String safe = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.length() > 100 ? safe.substring(safe.length() - 100) : safe;
    }

    private static String resolveContentType(String declared, String fileName) {
        String type = declared;
        if (type == null || type.trim().isEmpty() || MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(type)) {
            type = URLConnection.guessContentTypeFromName(fileName);
        }
        if (type == null || type.trim().isEmpty()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        try {
            String parsed = MediaType.parseMediaType(type).toString();
            // CU_ATTACHMENT.CONTENT_TYPE holds 100 characters; drop parameters if longer.
            return parsed.length() <= 100 ? parsed : parsed.split(";")[0].trim();
        } catch (RuntimeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }
}
