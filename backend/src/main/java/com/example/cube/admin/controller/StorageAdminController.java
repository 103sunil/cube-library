package com.example.cube.admin.controller;

import com.example.cube.admin.service.StorageAdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/storage")
public class StorageAdminController {

    private final StorageAdminService service;

    public StorageAdminController(StorageAdminService service) {
        this.service = service;
    }

    /** Active storage, whether GitHub is configured, and how many attachments live where. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return service.status();
    }

    /** Copies every attachment that is not on GitHub yet into the GitHub repository. */
    @PostMapping("/migrate")
    public Map<String, Object> migrate() {
        return service.migrateToGitHub();
    }
}
