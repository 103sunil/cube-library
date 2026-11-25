package com.example.cube.storage;

import com.example.cube.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubFileStorageServiceTest {

    @Test
    void placeholdersMeanNotConfiguredAndCallsFailWith503() {
        GitHubFileStorageService service = new GitHubFileStorageService("https://api.github.com",
                "CHANGE_ME_GITHUB_OWNER", "CHANGE_ME_GITHUB_REPO", "main", "CHANGE_ME_GITHUB_TOKEN", "attachments");

        assertFalse(service.isConfigured());
        ApiException ex = assertThrows(ApiException.class, () -> service.store("knowledge/1/a.txt", new byte[]{1}, "text/plain"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertThrows(ApiException.class, () -> service.load("attachments/knowledge/1/a.txt"));
    }

    @Test
    void realValuesAreConsideredConfigured() {
        GitHubFileStorageService service = new GitHubFileStorageService("https://api.github.com/",
                "acme", "knowledge-files", "main", "github_pat_xxx", "/attachments/");
        assertTrue(service.isConfigured());
    }
}
