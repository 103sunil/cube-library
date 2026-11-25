package com.example.cube.admin.service;

import com.example.cube.admin.dto.CreateUserRequest;
import com.example.cube.admin.dto.ImportResult;
import com.example.cube.admin.dto.UpdateUserRequest;
import com.example.cube.admin.dto.UserResponse;
import com.example.cube.audit.AuditService;
import com.example.cube.auth.Permissions;
import com.example.cube.auth.entity.Group;
import com.example.cube.auth.repository.GroupRepository;
import com.example.cube.auth.repository.UserRepository;
import com.example.cube.common.exception.ApiException;
import com.example.cube.security.CurrentUser;
import com.example.cube.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bulk onboarding from CSV (e.g. saved from Excel). Header row (any order, case-insensitive):
 * username, email, firstName, lastName, employeeId, designation, group, reportingManager.
 * group = EMPLOYEE / MANAGER / ADMIN (any active group code). reportingManager = a username that
 * already exists or appears in the same file, so managers can be imported together with their teams.
 * All-or-nothing: every row is validated first; any problem means nothing is created.
 * Everyone gets a random temporary password and must change it at first login.
 */
@Service
@Transactional
public class UserImportService {

    private static final String[] REQUIRED = {"username", "email", "group"};
    private static final String[] COLUMNS = {"username", "email", "firstname", "lastname", "employeeid",
            "designation", "group", "reportingmanager"};
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int MAX_ROWS = 2000;

    private final AdminUserService users;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final AuditService auditService;
    private final SecureRandom random = new SecureRandom();

    public UserImportService(AdminUserService users, UserRepository userRepository, GroupRepository groupRepository,
                             AuditService auditService) {
        this.users = users;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.auditService = auditService;
    }

    public ImportResult importCsv(String csv) {
        CurrentUser me = SecurityUtils.require(Permissions.MANAGE_USERS);
        ImportResult result = new ImportResult();
        List<String[]> rows = parse(csv);
        if (rows.isEmpty()) {
            result.getErrors().add("The file is empty");
            return result;
        }

        Map<String, Integer> col = new HashMap<>();
        String[] header = rows.get(0);
        for (int i = 0; i < header.length; i++) {
            col.put(normalize(header[i]), i);
        }
        if (col.containsKey("employeecode") && !col.containsKey("employeeid")) {
            col.put("employeeid", col.get("employeecode"));
        }
        if (col.containsKey("role") && !col.containsKey("designation")) {
            col.put("designation", col.get("role"));
        }
        for (String required : REQUIRED) {
            if (!col.containsKey(required)) {
                result.getErrors().add("Missing column '" + required + "'. Expected columns: username, email, "
                        + "firstName, lastName, employeeId, designation, group, reportingManager");
            }
        }
        if (!result.getErrors().isEmpty()) {
            return result;
        }
        if (rows.size() - 1 > MAX_ROWS) {
            result.getErrors().add("At most " + MAX_ROWS + " users per file");
            return result;
        }

        // ---- validate every row before creating anything
        List<Map<String, String>> records = new ArrayList<>();
        Set<String> usernames = new HashSet<>();
        Set<String> emails = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            if (isBlank(row)) {
                continue;
            }
            Map<String, String> rec = new HashMap<>();
            for (String key : COLUMNS) {
                Integer i = col.get(key);
                rec.put(key, i != null && i < row.length ? row[i].trim() : "");
            }
            int line = r + 1;
            String username = rec.get("username");
            String email = rec.get("email");
            String code = rec.get("employeeid");
            if (!username.matches("[A-Za-z0-9._-]{3,100}")) {
                result.getErrors().add("Line " + line + ": username '" + username
                        + "' must be 3-100 letters, digits, dots, dashes or underscores");
            } else if (!usernames.add(username.toLowerCase(Locale.ROOT)) || userRepository.existsByUsernameIgnoreCase(username)) {
                result.getErrors().add("Line " + line + ": username '" + username + "' already exists");
            }
            if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") || email.length() > 150) {
                result.getErrors().add("Line " + line + ": email '" + email + "' is not valid");
            } else if (!emails.add(email.toLowerCase(Locale.ROOT)) || userRepository.existsByEmailIgnoreCase(email)) {
                result.getErrors().add("Line " + line + ": email '" + email + "' is already used");
            }
            if (code.length() > 30) {
                result.getErrors().add("Line " + line + ": employee ID is longer than 30 characters");
            } else if (!code.isEmpty() && (!codes.add(code.toLowerCase(Locale.ROOT)) || userRepository.existsByEmployeeCodeIgnoreCase(code))) {
                result.getErrors().add("Line " + line + ": employee ID '" + code + "' is already used");
            }
            if (rec.get("designation").length() > 100) {
                result.getErrors().add("Line " + line + ": designation is longer than 100 characters");
            }
            Group group = groupRepository.findByGroupCode(rec.get("group").toUpperCase(Locale.ROOT)).orElse(null);
            if (group == null || !group.isActive()) {
                result.getErrors().add("Line " + line + ": group '" + rec.get("group")
                        + "' does not exist (use EMPLOYEE, MANAGER or ADMIN)");
            }
            rec.put("line", String.valueOf(line));
            records.add(rec);
        }
        for (Map<String, String> rec : records) {
            String manager = rec.get("reportingmanager");
            if (!manager.isEmpty() && !usernames.contains(manager.toLowerCase(Locale.ROOT))
                    && !userRepository.findByUsername(manager).isPresent()) {
                result.getErrors().add("Line " + rec.get("line") + ": reporting manager '" + manager
                        + "' is neither an existing user nor in this file");
            }
        }
        if (records.isEmpty() && result.getErrors().isEmpty()) {
            result.getErrors().add("The file has no user rows");
        }
        if (!result.getErrors().isEmpty()) {
            return result;
        }

        // ---- create everyone first, then link reporting managers (managers may be in the same file)
        Map<String, Long> ids = new HashMap<>();
        for (Map<String, String> rec : records) {
            String password = temporaryPassword();
            CreateUserRequest req = new CreateUserRequest();
            req.setUsername(rec.get("username"));
            req.setEmail(rec.get("email"));
            req.setFirstName(emptyToNull(rec.get("firstname")));
            req.setLastName(emptyToNull(rec.get("lastname")));
            req.setEmployeeCode(emptyToNull(rec.get("employeeid")));
            req.setDesignation(emptyToNull(rec.get("designation")));
            req.setGroupCode(rec.get("group").toUpperCase(Locale.ROOT));
            req.setPassword(password);
            UserResponse created = users.createUnchecked(req);
            ids.put(rec.get("username"), created.getUserId());
            result.getUsers().add(new ImportResult.CreatedUser(created.getUsername(), created.getEmployeeCode(),
                    created.getGroupCode(), emptyToNull(rec.get("reportingmanager")), password));
        }
        for (Map<String, String> rec : records) {
            String manager = rec.get("reportingmanager");
            if (!manager.isEmpty()) {
                UpdateUserRequest update = new UpdateUserRequest();
                update.setReportingManagerUsername(manager);
                try {
                    users.updateUnchecked(ids.get(rec.get("username")), update, me);
                } catch (ApiException ex) {
                    // The exception rolls back the transaction, so nothing is imported.
                    throw ApiException.badRequest("Line " + rec.get("line") + ": " + ex.getMessage()
                            + ". Nothing was imported.");
                }
            }
        }
        result.setCreated(records.size());
        auditService.record("USER_IMPORT", "USER", null, records.size() + " users imported");
        return result;
    }

    private static String normalize(String header) {
        String h = header.startsWith("﻿") ? header.substring(1) : header;
        return h.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "");
    }

    private static boolean isBlank(String[] row) {
        for (String cell : row) {
            if (!cell.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private String temporaryPassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        sb.append(2 + random.nextInt(8)); // always contains a digit
        return sb.toString();
    }

    /** Minimal CSV parser: comma or semicolon separated, double-quoted fields with "" escapes. */
    static List<String[]> parse(String text) {
        List<String[]> rows = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return rows;
        }
        String content = text.startsWith("﻿") ? text.substring(1) : text;
        String firstLine = content.split("\r?\n", 2)[0];
        char sep = firstLine.indexOf(';') >= 0 && firstLine.indexOf(',') < 0 ? ';' : ',';
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < content.length() && content.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == sep) {
                fields.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                fields.add(field.toString());
                field.setLength(0);
                rows.add(fields.toArray(new String[0]));
                fields.clear();
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !fields.isEmpty()) {
            fields.add(field.toString());
            rows.add(fields.toArray(new String[0]));
        }
        return rows;
    }
}
