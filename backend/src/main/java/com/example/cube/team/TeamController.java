package com.example.cube.team;

import com.example.cube.admin.dto.CreateUserRequest;
import com.example.cube.admin.dto.ResetPasswordRequest;
import com.example.cube.admin.dto.UpdateUserRequest;
import com.example.cube.admin.dto.UserOption;
import com.example.cube.admin.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/** Managers manage the employees who report to them. */
@RestController
@RequestMapping("/api/v1/team")
public class TeamController {

    private final TeamService service;

    public TeamController(TeamService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserResponse> myTeam() {
        return service.myTeam();
    }

    @GetMapping("/managers")
    public List<UserOption> managers() {
        return service.managers();
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

    /** Removes a team member who left the company. */
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    public java.util.Map<String, Object> remove(@PathVariable Long id) {
        return service.remove(id);
    }
}
