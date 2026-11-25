package com.example.cube.admin.service;

import com.example.cube.audit.AuditService;
import com.example.cube.auth.Permissions;
import com.example.cube.common.exception.ApiException;
import com.example.cube.knowledge.entity.Attachment;
import com.example.cube.knowledge.repository.AttachmentRepository;
import com.example.cube.knowledge.repository.KnowledgeRepository;
import com.example.cube.knowledge.service.AttachmentService;
import com.example.cube.security.SecurityUtils;
import com.example.cube.storage.AttachmentFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin view of where attachments are stored, and a one-click copy of every attachment that is not
 * yet on GitHub (local disk, previous-version disk paths, database BLOBs) into the GitHub repository.
 * Originals are left untouched; only CU_ATTACHMENT.ONEDRIVE_FILE_ID is pointed at the GitHub copy.
 * Each attachment is saved on its own, so the copy can be re-run after fixing missing files.
 */
@Service
public class StorageAdminService {

    private static final Logger log = LoggerFactory.getLogger(StorageAdminService.class);

    private final AttachmentRepository attachmentRepository;
    private final KnowledgeRepository knowledgeRepository;
    private final AttachmentFiles files;
    private final AuditService auditService;

    public StorageAdminService(AttachmentRepository attachmentRepository, KnowledgeRepository knowledgeRepository,
                               AttachmentFiles files, AuditService auditService) {
        this.attachmentRepository = attachmentRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.files = files;
        this.auditService = auditService;
    }

    public Map<String, Object> status() {
        SecurityUtils.require(Permissions.MANAGE_MODULES);
        Map<AttachmentFiles.Location, Integer> counts = new EnumMap<>(AttachmentFiles.Location.class);
        for (AttachmentFiles.Location l : AttachmentFiles.Location.values()) {
            counts.put(l, 0);
        }
        for (Attachment a : attachmentRepository.findAll()) {
            counts.merge(files.location(a.getStorageKey()), 1, Integer::sum);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("storageType", files.storageType());
        result.put("githubConfigured", files.isGitHubConfigured());
        Map<String, Integer> byLocation = new LinkedHashMap<>();
        byLocation.put("github", counts.get(AttachmentFiles.Location.GITHUB));
        byLocation.put("local", counts.get(AttachmentFiles.Location.LOCAL));
        byLocation.put("legacyDisk", counts.get(AttachmentFiles.Location.LEGACY_DISK));
        byLocation.put("database", counts.get(AttachmentFiles.Location.DATABASE));
        result.put("counts", byLocation);
        int notOnGitHub = byLocation.get("local") + byLocation.get("legacyDisk") + byLocation.get("database");
        result.put("notOnGitHub", notOnGitHub);
        return result;
    }

    public Map<String, Object> migrateToGitHub() {
        SecurityUtils.require(Permissions.MANAGE_MODULES);
        if (!files.isGitHub()) {
            throw ApiException.badRequest("The server is using local storage. Set CUBE_STORAGE_TYPE=github "
                    + "and the GitHub details, restart, then copy the files.");
        }
        if (!files.isGitHubConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GitHub is not configured yet "
                    + "(CUBE_GITHUB_OWNER, CUBE_GITHUB_REPO, CUBE_GITHUB_TOKEN).");
        }
        int copied = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (Attachment a : attachmentRepository.findAll()) {
            if (files.location(a.getStorageKey()) == AttachmentFiles.Location.GITHUB) {
                continue;
            }
            if (!knowledgeRepository.existsById(a.getKnowledgeId())) {
                skipped++; // belongs to a deleted item
                continue;
            }
            String label = "Attachment " + a.getAttachmentId() + " (" + a.getFileName() + ", item "
                    + a.getKnowledgeId() + ")";
            byte[] bytes;
            try {
                bytes = files.load(a.getAttachmentId(), a.getStorageKey());
            } catch (ApiException ex) {
                errors.add(label + ": file not found where it was stored");
                continue;
            }
            String path = "knowledge/" + a.getKnowledgeId() + "/" + UUID.randomUUID().toString().replace("-", "")
                    + "_" + AttachmentService.storageSafeName(a.getFileName() == null ? "file" : a.getFileName());
            try {
                String key = files.storage().store(path, bytes, a.getContentType());
                a.setStorageKey(key);
                if (a.getFileSize() == null) {
                    a.setFileSize((long) bytes.length);
                }
                attachmentRepository.save(a);
                copied++;
            } catch (ApiException ex) {
                log.error("Copy to GitHub failed for attachment {}: {}", a.getAttachmentId(), ex.getMessage());
                errors.add(label + ": " + ex.getMessage());
                if (ex.getStatus() == HttpStatus.BAD_GATEWAY || ex.getStatus() == HttpStatus.SERVICE_UNAVAILABLE) {
                    errors.add("Stopped: GitHub is not reachable or rejected the token. Fix it and run the copy again.");
                    break;
                }
            }
        }
        auditService.record("STORAGE_MIGRATE", "ATTACHMENT", null, copied + " copied to GitHub, "
                + errors.size() + " problems");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("copied", copied);
        result.put("skipped", skipped);
        result.put("errors", errors);
        return result;
    }
}
