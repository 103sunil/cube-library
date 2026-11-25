package com.example.cube;

import com.example.cube.auth.entity.Access;
import com.example.cube.auth.entity.Group;
import com.example.cube.auth.entity.GroupAccessTemplate;
import com.example.cube.auth.entity.GroupAccessTemplateId;
import com.example.cube.auth.entity.Module;
import com.example.cube.auth.entity.User;
import com.example.cube.auth.entity.UserGroup;
import com.example.cube.auth.repository.AccessRepository;
import com.example.cube.auth.repository.GroupAccessTemplateRepository;
import com.example.cube.auth.repository.GroupRepository;
import com.example.cube.auth.repository.ModuleRepository;
import com.example.cube.auth.repository.UserGroupRepository;
import com.example.cube.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** Mirrors the roles and codes in CUBEDB (AUTH / KNOWLEDGE modules, ADMIN / EMPLOYEE / MANAGER groups). */
@Component
public class TestData {

    public static final String PASSWORD = "Passw0rd!";

    private final UserRepository users;
    private final GroupRepository groups;
    private final UserGroupRepository userGroups;
    private final ModuleRepository modules;
    private final AccessRepository accesses;
    private final GroupAccessTemplateRepository grants;
    private final PasswordEncoder encoder;

    public TestData(UserRepository users, GroupRepository groups, UserGroupRepository userGroups,
                    ModuleRepository modules, AccessRepository accesses, GroupAccessTemplateRepository grants,
                    PasswordEncoder encoder) {
        this.users = users;
        this.groups = groups;
        this.userGroups = userGroups;
        this.modules = modules;
        this.accesses = accesses;
        this.grants = grants;
        this.encoder = encoder;
    }

    public synchronized void seed() {
        if (groups.findByGroupCode("EMPLOYEE").isPresent()) {
            resetBaselineUsers();
            return;
        }
        Module auth = module("AUTH");
        Module knowledge = module("KNOWLEDGE");
        Map<String, Access> a = new HashMap<>();
        for (String code : new String[]{"MANAGE_USERS", "MANAGE_GROUPS", "MANAGE_MODULES", "MANAGE_ACCESS",
                "MANAGE_GROUP_ACCESS", "MANAGE_USER_ACCESS"}) {
            a.put(code, access(auth, code));
        }
        for (String code : new String[]{"CREATE", "APPROVE", "VIEW", "SEARCH", "DOWNLOAD", "UPDATE", "DELETE"}) {
            a.put(code, access(knowledge, code));
        }

        Group admin = group("ADMIN");
        Group employee = group("EMPLOYEE");
        Group manager = group("MANAGER");
        for (String code : new String[]{"MANAGE_USERS", "MANAGE_GROUPS", "MANAGE_MODULES", "MANAGE_ACCESS",
                "MANAGE_GROUP_ACCESS", "MANAGE_USER_ACCESS"}) {
            grant(admin, a.get(code), "Y");
        }
        for (String code : new String[]{"CREATE", "VIEW", "SEARCH", "DOWNLOAD"}) {
            grant(employee, a.get(code), "Y");
        }
        grant(employee, a.get("APPROVE"), "N");
        for (String code : new String[]{"CREATE", "APPROVE", "VIEW", "SEARCH", "DOWNLOAD", "UPDATE", "DELETE"}) {
            grant(manager, a.get(code), "Y");
        }

        User mgr1 = user("mgr1", manager, "A", null);
        user("mgr2", manager, "A", null);
        user("emp1", employee, "A", mgr1.getUserId());
        user("emp2", employee, "A", mgr1.getUserId());
        user("emp3", employee, "A", null);
        user("emp4", employee, "A", null); // no reporting manager -> shared queue
        user("admin1", admin, "A", null);
        user("inactive1", employee, "I", null);
    }

    /** Tests share one database: undo deletes / manager moves made to the standard users by earlier tests. */
    private void resetBaselineUsers() {
        Long mgr1 = users.findByUsername("mgr1").map(User::getUserId).orElse(null);
        String[][] baseline = {
                {"mgr1", "A", null}, {"mgr2", "A", null}, {"emp1", "A", "mgr1"}, {"emp2", "A", "mgr1"},
                {"emp3", "A", null}, {"emp4", "A", null}, {"admin1", "A", null}, {"inactive1", "I", null}};
        for (String[] b : baseline) {
            users.findByUsername(b[0]).ifPresent(u -> {
                u.setStatus(b[1]);
                u.setReportingManagerId(b[2] == null ? null : mgr1);
                users.save(u);
            });
        }
    }

    private Module module(String code) {
        Module m = new Module();
        m.setModuleCode(code);
        m.setModuleName(code);
        m.setStatus("A");
        m.setCreatedAt(LocalDateTime.now());
        return modules.save(m);
    }

    private Access access(Module module, String code) {
        Access a = new Access();
        a.setModuleId(module.getModuleId());
        a.setAccessCode(code);
        a.setDescription(code);
        a.setStatus("A");
        a.setCreatedAt(LocalDateTime.now());
        return accesses.save(a);
    }

    private Group group(String code) {
        Group g = new Group();
        g.setGroupCode(code);
        g.setGroupName(code);
        g.setStatus("A");
        g.setCreatedAt(LocalDateTime.now());
        return groups.save(g);
    }

    private void grant(Group g, Access a, String allowed) {
        GroupAccessTemplate t = new GroupAccessTemplate();
        t.setId(new GroupAccessTemplateId(g.getGroupId(), a.getAccessId()));
        t.setAllowed(allowed);
        t.setStatus("A");
        t.setCreatedAt(LocalDateTime.now());
        grants.save(t);
    }

    private User user(String username, Group g, String status, Long reportingManagerId) {
        User u = new User();
        u.setReportingManagerId(reportingManagerId);
        u.setUsername(username);
        u.setEmail(username + "@example.com");
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setFirstName(username);
        u.setStatus(status);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        u = users.save(u);

        UserGroup ug = new UserGroup();
        ug.setUserId(u.getUserId());
        ug.setGroupId(g.getGroupId());
        ug.setStatus("A");
        ug.setCreatedAt(LocalDateTime.now());
        userGroups.save(ug);
        return u;
    }
}
