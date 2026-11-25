package com.example.cube.admin.controller;

import com.example.cube.admin.dto.CreateUserRequest;
import com.example.cube.admin.dto.GrantRequest;
import com.example.cube.admin.dto.GrantResponse;
import com.example.cube.admin.dto.ResetPasswordRequest;
import com.example.cube.admin.dto.UpdateUserRequest;
import com.example.cube.admin.dto.UserOption;
import com.example.cube.admin.dto.UserResponse;
import com.example.cube.admin.dto.ImportResult;
import com.example.cube.admin.service.AdminUserService;
import com.example.cube.admin.service.UserImportService;
import com.example.cube.common.response.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

/** Users are never hard-deleted (they own knowledge items); set status to I to deactivate. */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService service;
    private final UserImportService importService;

    public AdminUserController(AdminUserService service, UserImportService importService) {
        this.service = service;
        this.importService = importService;
    }

    /** Bulk onboarding from a CSV file (multipart field "file"). All-or-nothing. */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importUsers(@RequestParam("file") MultipartFile file) throws IOException {
        return importService.importCsv(new String(file.getBytes(), StandardCharsets.UTF_8));
    }

    @GetMapping
    public PageResponse<UserResponse> list(@RequestParam(value = "q", required = false) String q,
                                           @RequestParam(value = "removed", defaultValue = "false") boolean removed,
                                           @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size) {
        return service.list(q, removed, page, size);
    }

    /** Removes a user who left. Their knowledge stays; their team moves up to their manager. */
    @DeleteMapping("/{id}")
    public java.util.Map<String, Object> remove(@PathVariable Long id) {
        return service.remove(id);
    }

    @PostMapping("/{id}/restore")
    public UserResponse restore(@PathVariable Long id) {
        return service.restore(id);
    }

    /** Every active user: the choices for "Reports to" (managers, team leads, ...). */
    @GetMapping("/options")
    public List<UserOption> reportingOptions() {
        return service.reportingOptions();
    }

    /** Users who can approve knowledge. */
    @GetMapping("/approvers")
    public List<UserOption> approvers() {
        return service.approvers();
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return service.update(id, request);
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(id, request.getPassword());
        return ResponseEntity.noContent().build();
    }

    /** Effective permissions after group defaults and overrides. */
    @GetMapping("/{id}/permissions")
    public List<String> permissions(@PathVariable Long id) {
        return service.effectivePermissions(id);
    }

    @GetMapping("/{id}/overrides")
    public List<GrantResponse> overrides(@PathVariable Long id) {
        return service.listOverrides(id);
    }

    @PutMapping("/{id}/overrides")
    public List<GrantResponse> upsertOverride(@PathVariable Long id, @Valid @RequestBody GrantRequest request) {
        return service.upsertOverride(id, request);
    }

    @DeleteMapping("/{id}/overrides/{moduleCode}/{accessCode}")
    public ResponseEntity<Void> deleteOverride(@PathVariable Long id, @PathVariable String moduleCode,
                                               @PathVariable String accessCode) {
        service.deleteOverride(id, moduleCode, accessCode);
        return ResponseEntity.noContent().build();
    }
}
