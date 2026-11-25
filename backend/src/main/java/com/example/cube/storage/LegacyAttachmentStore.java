package com.example.cube.storage;

import com.example.cube.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads attachments uploaded by the previous version of Cube:
 *  - no storage key: the bytes are in CU_ATTACHMENT.FILE_DATA (BLOB)
 *  - an absolute path such as D:/cube/./local-storage/knowledge-61/(id)_name.png (Windows separators)
 *  - a relative path such as knowledge-201/(id)_name.docx
 *
 * Files are looked up at their original absolute path first, then under every folder listed in
 * cube.storage.legacy-dirs (CUBE_LEGACY_FILES_DIR, comma separated) using the "knowledge-N\..." part.
 * Copy the old "local-storage" folders from the machines that ran the previous version into one of those folders.
 */
@Component
public class LegacyAttachmentStore {

    private static final Logger log = LoggerFactory.getLogger(LegacyAttachmentStore.class);
    private static final String MARKER = "knowledge-";

    @PersistenceContext
    private EntityManager entityManager;

    private final List<Path> legacyDirs = new ArrayList<>();

    public LegacyAttachmentStore(@Value("${cube.storage.legacy-dirs:}") String dirs) {
        for (String dir : dirs.split(",")) {
            if (!dir.trim().isEmpty()) {
                legacyDirs.add(Paths.get(dir.trim()).toAbsolutePath().normalize());
            }
        }
        if (!legacyDirs.isEmpty()) {
            log.info("Old attachment folders: {}", legacyDirs);
        }
    }

    /** True for keys written by the previous version (not by the current storage service). */
    public static boolean isLegacyKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return true;
        }
        String k = key.trim();
        return k.matches("^[A-Za-z]:[\\\\/].*") || k.startsWith("\\\\") || k.startsWith(MARKER);
    }

    public byte[] load(Long attachmentId, String key) {
        if (key == null || key.trim().isEmpty()) {
            return loadBlob(attachmentId);
        }
        for (Path candidate : candidates(key.trim())) {
            if (Files.isRegularFile(candidate)) {
                try {
                    return Files.readAllBytes(candidate);
                } catch (IOException ex) {
                    log.warn("Could not read old attachment {}: {}", candidate, ex.getMessage());
                }
            }
        }
        log.warn("Old attachment {} not found (key {}), looked in {}", attachmentId, key, legacyDirs);
        throw ApiException.notFound("This file was uploaded with the previous version of Cube and is not available "
                + "on this server. Ask the administrator to copy the old files (CUBE_LEGACY_FILES_DIR).");
    }

    private byte[] loadBlob(Long attachmentId) {
        Object value;
        try {
            List<?> rows = entityManager
                    .createNativeQuery("SELECT FILE_DATA FROM CU_ATTACHMENT WHERE ATTACHMENT_ID = :id")
                    .setParameter("id", attachmentId)
                    .getResultList();
            value = rows.isEmpty() ? null : rows.get(0);
        } catch (RuntimeException ex) {
            log.warn("Could not read FILE_DATA for attachment {}: {}", attachmentId, ex.getMessage());
            value = null;
        }
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        if (value instanceof Blob) {
            Blob blob = (Blob) value;
            try {
                return blob.getBytes(1, (int) blob.length());
            } catch (SQLException ex) {
                throw ApiException.notFound("Stored file could not be read");
            }
        }
        throw ApiException.notFound("Stored file not found");
    }

    private List<Path> candidates(String key) {
        Set<Path> result = new LinkedHashSet<>();
        // 1. The original absolute path, only inside a "local-storage" folder of the old app.
        if (!key.startsWith(MARKER) && key.toLowerCase().contains("local-storage")) {
            addPath(result, null, key);
        }
        // 2. "knowledge-N\file" under each configured legacy folder (also URL-decoded names).
        int idx = key.indexOf(MARKER);
        if (idx >= 0) {
            String tail = key.substring(idx).replace('\\', '/');
            for (Path dir : legacyDirs) {
                addPath(result, dir, tail);
                addPath(result, dir, decode(tail));
            }
        }
        return new ArrayList<>(result);
    }

    private static void addPath(Set<Path> paths, Path base, String relative) {
        try {
            Path p = base == null ? Paths.get(relative).normalize() : base.resolve(relative).normalize();
            if (base == null || p.startsWith(base)) {
                paths.add(p);
            }
        } catch (InvalidPathException ex) {
            // ignore unusable names
        }
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException ex) {
            return value;
        }
    }
}
