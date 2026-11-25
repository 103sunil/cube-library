package com.example.cube.knowledge.controller;

import com.example.cube.knowledge.dto.AttachmentContent;
import com.example.cube.knowledge.service.AttachmentService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/attachments")
public class AttachmentController {

    /** Types that are safe to render inline. Everything else is previewed as a plain download. */
    private static final Set<String> INLINE_SAFE_TYPES = new HashSet<>(Arrays.asList(
            "application/pdf", "image/png", "image/jpeg", "image/gif", "image/webp", "image/bmp",
            "text/plain", "text/csv"));

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        AttachmentContent content = attachmentService.download(id);
        return build(content, "attachment", mediaType(content.getContentType()));
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable Long id) {
        AttachmentContent content = attachmentService.download(id);
        MediaType type = mediaType(content.getContentType());
        boolean safe = INLINE_SAFE_TYPES.contains(type.getType() + "/" + type.getSubtype());
        return build(content, safe ? "inline" : "attachment", safe ? type : MediaType.APPLICATION_OCTET_STREAM);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        attachmentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<byte[]> build(AttachmentContent content, String disposition, MediaType type) {
        ContentDisposition cd = ContentDisposition.builder(disposition)
                .filename(content.getFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(type)
                .contentLength(content.getData().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(content.getData());
    }

    private static MediaType mediaType(String value) {
        try {
            return value == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(value);
        } catch (RuntimeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
