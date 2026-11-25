package com.example.cube.storage;

import com.example.cube.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Reads attachment bytes from wherever they currently live, so that switching cube.storage.type
 * (e.g. from local to github) never makes older files unreachable:
 *  - DATABASE    : no key; bytes in CU_ATTACHMENT.FILE_DATA (previous version of Cube)
 *  - LEGACY_DISK : absolute/relative path written by the previous version
 *  - LOCAL       : "knowledge/N/..." under cube.storage.local.base-dir
 *  - GITHUB      : "attachments/knowledge/N/..." in the GitHub repository
 */
@Component
public class AttachmentFiles {

    public enum Location { DATABASE, LEGACY_DISK, LOCAL, GITHUB }

    private final FileStorageService storage;
    private final LegacyAttachmentStore legacyStore;
    private final String storageType;
    private final String githubPrefix;
    private final LocalFileStorageService localDisk;

    public AttachmentFiles(FileStorageService storage, LegacyAttachmentStore legacyStore,
                           @Value("${cube.storage.type:github}") String storageType,
                           @Value("${cube.storage.github.base-path:attachments}") String githubBasePath,
                           @Value("${cube.storage.local.base-dir:${user.home}/cube-files}") String localBaseDir) {
        this.storage = storage;
        this.legacyStore = legacyStore;
        this.storageType = storageType.trim().toLowerCase();
        String base = githubBasePath == null ? "" : githubBasePath.trim().replaceAll("^/+|/+$", "");
        this.githubPrefix = base.isEmpty() ? "" : base + "/";
        this.localDisk = storage instanceof LocalFileStorageService
                ? (LocalFileStorageService) storage : new LocalFileStorageService(localBaseDir);
    }

    public String storageType() {
        return storageType;
    }

    public boolean isGitHub() {
        return storage instanceof GitHubFileStorageService;
    }

    public boolean isGitHubConfigured() {
        return isGitHub() && ((GitHubFileStorageService) storage).isConfigured();
    }

    public FileStorageService storage() {
        return storage;
    }

    public Location location(String key) {
        if (key == null || key.trim().isEmpty()) {
            return Location.DATABASE;
        }
        if (LegacyAttachmentStore.isLegacyKey(key)) {
            return Location.LEGACY_DISK;
        }
        if (!githubPrefix.isEmpty() && key.startsWith(githubPrefix)) {
            return Location.GITHUB;
        }
        if (githubPrefix.isEmpty() && isGitHub()) {
            return Location.GITHUB;
        }
        return Location.LOCAL;
    }

    /** True if the file is managed by the active storage (so it may be deleted there). */
    public boolean inActiveStorage(String key) {
        Location loc = location(key);
        return (loc == Location.GITHUB && isGitHub()) || (loc == Location.LOCAL && !isGitHub());
    }

    public byte[] load(Long attachmentId, String key) {
        switch (location(key)) {
            case DATABASE:
            case LEGACY_DISK:
                return legacyStore.load(attachmentId, key);
            case LOCAL:
                return localDisk.load(key);
            case GITHUB:
            default:
                if (!isGitHub()) {
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                            "This file is stored on GitHub but the server is set to local storage. "
                                    + "Please contact the administrator.");
                }
                return storage.load(key);
        }
    }
}
