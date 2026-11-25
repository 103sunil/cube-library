package com.example.cube.storage;

import com.example.cube.common.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores attachments in a GitHub repository through the REST "contents" API.
 *
 * Configuration (application.properties / environment):
 *   cube.storage.github.owner   - user or organisation that owns the repo   (CUBE_GITHUB_OWNER)
 *   cube.storage.github.repo    - repository name, should be PRIVATE        (CUBE_GITHUB_REPO)
 *   cube.storage.github.branch  - branch to commit to (default main)        (CUBE_GITHUB_BRANCH)
 *   cube.storage.github.token   - fine-grained token with Contents: read/write on that repo only (CUBE_GITHUB_TOKEN)
 *
 * Until real values are supplied, every storage call fails with 503 and a clear message.
 * The server must be able to reach api.github.com (set -Dhttps.proxyHost / -Dhttps.proxyPort behind a proxy).
 */
@Service
@ConditionalOnProperty(name = "cube.storage.type", havingValue = "github", matchIfMissing = true)
public class GitHubFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(GitHubFileStorageService.class);
    private static final String PLACEHOLDER_MARKER = "CHANGE_ME";
    private static final int MAX_CONFLICT_RETRIES = 3;

    private final String apiUrl;
    private final String owner;
    private final String repo;
    private final String branch;
    private final String token;
    private final String basePath;
    private final RestTemplate restTemplate;

    public GitHubFileStorageService(@Value("${cube.storage.github.api-url:https://api.github.com}") String apiUrl,
                                    @Value("${cube.storage.github.owner:}") String owner,
                                    @Value("${cube.storage.github.repo:}") String repo,
                                    @Value("${cube.storage.github.branch:main}") String branch,
                                    @Value("${cube.storage.github.token:}") String token,
                                    @Value("${cube.storage.github.base-path:attachments}") String basePath) {
        this.apiUrl = stripTrailingSlash(apiUrl);
        this.owner = owner.trim();
        this.repo = repo.trim();
        this.branch = branch.trim();
        this.token = token.trim();
        this.basePath = trimSlashes(basePath);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(60_000);
        this.restTemplate = new RestTemplate(factory);
    }

    @PostConstruct
    void warnIfNotConfigured() {
        if (!isConfigured()) {
            log.warn("GitHub file storage is NOT configured (owner/repo/token are placeholders). "
                    + "Attachment upload and download will return 503 until CUBE_GITHUB_OWNER, "
                    + "CUBE_GITHUB_REPO and CUBE_GITHUB_TOKEN are set.");
        }
    }

    public boolean isConfigured() {
        return !owner.isEmpty() && !repo.isEmpty() && !token.isEmpty()
                && !owner.contains(PLACEHOLDER_MARKER) && !repo.contains(PLACEHOLDER_MARKER)
                && !token.contains(PLACEHOLDER_MARKER);
    }

    @Override
    public String store(String path, byte[] content, String contentType) {
        requireConfigured();
        String repoPath = basePath.isEmpty() ? trimSlashes(path) : basePath + "/" + trimSlashes(path);

        Map<String, Object> body = new HashMap<>();
        body.put("message", "Add attachment " + repoPath);
        body.put("content", Base64.getEncoder().encodeToString(content));
        body.put("branch", branch);

        for (int attempt = 1; ; attempt++) {
            try {
                restTemplate.exchange(contentsUri(repoPath, false), HttpMethod.PUT,
                        new HttpEntity<>(body, headers(MediaType.APPLICATION_JSON_VALUE)), JsonNode.class);
                return repoPath;
            } catch (HttpStatusCodeException ex) {
                // 409 happens when two commits hit the branch at the same moment; retry.
                if (ex.getRawStatusCode() == 409 && attempt < MAX_CONFLICT_RETRIES) {
                    sleepQuietly(300L * attempt);
                    continue;
                }
                throw translate("upload", repoPath, ex);
            } catch (ResourceAccessException ex) {
                throw unreachable(ex);
            }
        }
    }

    @Override
    public byte[] load(String storageKey) {
        requireConfigured();
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(contentsUri(storageKey, true), HttpMethod.GET,
                    new HttpEntity<Void>(headers("application/vnd.github.raw+json")), byte[].class);
            byte[] bytes = response.getBody();
            return bytes == null ? new byte[0] : bytes;
        } catch (HttpStatusCodeException ex) {
            throw translate("download", storageKey, ex);
        } catch (ResourceAccessException ex) {
            throw unreachable(ex);
        }
    }

    @Override
    public void delete(String storageKey) {
        requireConfigured();
        try {
            ResponseEntity<JsonNode> meta = restTemplate.exchange(contentsUri(storageKey, true), HttpMethod.GET,
                    new HttpEntity<Void>(headers("application/vnd.github+json")), JsonNode.class);
            JsonNode node = meta.getBody();
            if (node == null || !node.hasNonNull("sha")) {
                return;
            }
            Map<String, Object> body = new HashMap<>();
            body.put("message", "Delete attachment " + storageKey);
            body.put("sha", node.get("sha").asText());
            body.put("branch", branch);
            restTemplate.exchange(contentsUri(storageKey, false), HttpMethod.DELETE,
                    new HttpEntity<>(body, headers(MediaType.APPLICATION_JSON_VALUE)), JsonNode.class);
        } catch (HttpStatusCodeException ex) {
            if (ex.getRawStatusCode() == 404) {
                return;
            }
            throw translate("delete", storageKey, ex);
        } catch (ResourceAccessException ex) {
            throw unreachable(ex);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "File storage is not configured yet (GitHub account details missing). Please contact the administrator.");
        }
    }

    private URI contentsUri(String repoPath, boolean withRef) {
        StringBuilder sb = new StringBuilder(apiUrl)
                .append("/repos/")
                .append(UriUtils.encodePathSegment(owner, StandardCharsets.UTF_8))
                .append('/')
                .append(UriUtils.encodePathSegment(repo, StandardCharsets.UTF_8))
                .append("/contents");
        for (String segment : repoPath.split("/")) {
            if (!segment.isEmpty()) {
                sb.append('/').append(UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8));
            }
        }
        if (withRef) {
            sb.append("?ref=").append(UriUtils.encodeQueryParam(branch, StandardCharsets.UTF_8));
        }
        return URI.create(sb.toString());
    }

    private HttpHeaders headers(String accept) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(Collections.singletonList(MediaType.parseMediaType(accept)));
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        headers.set(HttpHeaders.USER_AGENT, "cube-knowledge-service");
        return headers;
    }

    private ApiException translate(String operation, String path, HttpStatusCodeException ex) {
        int status = ex.getRawStatusCode();
        log.error("GitHub storage {} failed for {}: HTTP {} {}", operation, path, status, ex.getResponseBodyAsString());
        if (status == 404 && "download".equals(operation)) {
            return ApiException.notFound("Stored file not found");
        }
        if (status == 401 || status == 403) {
            return new ApiException(HttpStatus.BAD_GATEWAY,
                    "File storage rejected the credentials. Please contact the administrator.");
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, "File storage error during " + operation + ". Please try again.");
    }

    private ApiException unreachable(ResourceAccessException ex) {
        log.error("GitHub storage unreachable: {}", ex.getMessage());
        return new ApiException(HttpStatus.BAD_GATEWAY, "File storage is unreachable. Please try again later.");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String stripTrailingSlash(String value) {
        String v = value.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private static String trimSlashes(String value) {
        String v = value == null ? "" : value.trim();
        while (v.startsWith("/")) {
            v = v.substring(1);
        }
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }
}
