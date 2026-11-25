package com.example.cube.admin.controller;

import com.example.cube.admin.dto.GrantRequest;
import com.example.cube.admin.dto.GrantResponse;
import com.example.cube.admin.dto.GroupRequest;
import com.example.cube.admin.dto.GroupResponse;
import com.example.cube.admin.service.AdminGroupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/groups")
public class AdminGroupController {

    private final AdminGroupService service;

    public AdminGroupController(AdminGroupService service) {
        this.service = service;
    }

    @GetMapping
    public List<GroupResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public GroupResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<GroupResponse> create(@Valid @RequestBody GroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public GroupResponse update(@PathVariable Long id, @Valid @RequestBody GroupRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/grants")
    public List<GrantResponse> grants(@PathVariable Long id) {
        return service.listGrants(id);
    }

    @PutMapping("/{id}/grants")
    public List<GrantResponse> upsertGrant(@PathVariable Long id, @Valid @RequestBody GrantRequest request) {
        return service.upsertGrant(id, request);
    }

    @DeleteMapping("/{id}/grants/{moduleCode}/{accessCode}")
    public ResponseEntity<Void> deleteGrant(@PathVariable Long id, @PathVariable String moduleCode,
                                            @PathVariable String accessCode) {
        service.deleteGrant(id, moduleCode, accessCode);
        return ResponseEntity.noContent().build();
    }
}
