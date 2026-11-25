package com.example.cube.admin.controller;

import com.example.cube.admin.dto.AuditResponse;
import com.example.cube.admin.dto.CatalogItemRequest;
import com.example.cube.admin.dto.CatalogItemResponse;
import com.example.cube.admin.service.AdminAuditService;
import com.example.cube.admin.service.AdminCatalogService;
import com.example.cube.common.response.PageResponse;
import org.springframework.http.HttpStatus;
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

import javax.validation.Valid;
import java.util.List;

/** Modules, access codes and the audit log. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminCatalogController {

    private final AdminCatalogService catalogService;
    private final AdminAuditService auditService;

    public AdminCatalogController(AdminCatalogService catalogService, AdminAuditService auditService) {
        this.catalogService = catalogService;
        this.auditService = auditService;
    }

    @GetMapping("/modules")
    public List<CatalogItemResponse> modules() {
        return catalogService.listModules();
    }

    @PostMapping("/modules")
    public ResponseEntity<CatalogItemResponse> createModule(@Valid @RequestBody CatalogItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.createModule(request));
    }

    @PutMapping("/modules/{id}")
    public CatalogItemResponse updateModule(@PathVariable Long id, @Valid @RequestBody CatalogItemRequest request) {
        return catalogService.updateModule(id, request);
    }

    @DeleteMapping("/modules/{id}")
    public ResponseEntity<Void> deleteModule(@PathVariable Long id) {
        catalogService.deleteModule(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/access-codes")
    public List<CatalogItemResponse> accessCodes() {
        return catalogService.listAccessCodes();
    }

    @PostMapping("/access-codes")
    public ResponseEntity<CatalogItemResponse> createAccess(@Valid @RequestBody CatalogItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.createAccess(request));
    }

    @PutMapping("/access-codes/{id}")
    public CatalogItemResponse updateAccess(@PathVariable Long id, @Valid @RequestBody CatalogItemRequest request) {
        return catalogService.updateAccess(id, request);
    }

    @DeleteMapping("/access-codes/{id}")
    public ResponseEntity<Void> deleteAccess(@PathVariable Long id) {
        catalogService.deleteAccess(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit")
    public PageResponse<AuditResponse> audit(@RequestParam(required = false) String actor,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size) {
        return auditService.list(actor, page, size);
    }
}
