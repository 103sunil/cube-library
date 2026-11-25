package com.example.cube;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Employee ID / role, bulk import of managers and teams, first-login password change, storage status. */
@SpringBootTest
@AutoConfigureMockMvc
class OnboardingTest {

    private static final String API = "/api/v1";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TestData testData;

    @BeforeEach
    void seed() {
        testData.seed();
    }

    @Test
    void managerAddsEmployeeWithIdAndRoleAndTheyMustChangePassword() throws Exception {
        String mgr = login("mgr1", TestData.PASSWORD);
        mvc.perform(post(API + "/team").header("Authorization", bearer(mgr)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"lead1\",\"email\":\"lead1@example.com\",\"password\":\"Temp1234!\","
                                + "\"employeeCode\":\"E1001\",\"designation\":\"Team Lead\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeCode").value("E1001"))
                .andExpect(jsonPath("$.designation").value("Team Lead"))
                .andExpect(jsonPath("$.groupCode").value("EMPLOYEE"));

        // Employee IDs are unique (case-insensitive).
        mvc.perform(post(API + "/team").header("Authorization", bearer(mgr)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"lead2\",\"email\":\"lead2@example.com\",\"password\":\"Temp1234!\","
                                + "\"employeeCode\":\"e1001\"}"))
                .andExpect(status().isConflict());

        // First login: must change the temporary password.
        String lead = login("lead1", "Temp1234!");
        mvc.perform(get(API + "/me").header("Authorization", bearer(lead)))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.employeeCode").value("E1001"));
        mvc.perform(post(API + "/me/password").header("Authorization", bearer(lead)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"NewPass123\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(API + "/me/password").header("Authorization", bearer(lead)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Temp1234!\",\"newPassword\":\"NewPass123\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get(API + "/me").header("Authorization", bearer(lead)))
                .andExpect(jsonPath("$.mustChangePassword").value(false));
        login("lead1", "NewPass123");
    }

    @Test
    void adminImportsManagersAndTheirTeamsFromCsv() throws Exception {
        String admin = login("admin1", TestData.PASSWORD);
        String csv = "username,email,firstName,lastName,employeeId,designation,group,reportingManager\n"
                + "newmgr,newmgr@example.com,Nina,Rao,M200,Engineering Manager,MANAGER,\n"
                + "dev1,dev1@example.com,Dev,One,E201,Senior Employee,EMPLOYEE,newmgr\n"
                + "\"dev2\",dev2@example.com,\"Dev, Two\",,E202,Employee,employee,mgr1\n";
        MvcResult result = mvc.perform(multipart(API + "/admin/users/import")
                        .file(new MockMultipartFile("file", "users.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(3))
                .andExpect(jsonPath("$.errors.length()").value(0))
                .andReturn();
        String dev1Password = JsonPath.read(body(result), "$.users[1].temporaryPassword");

        // dev1 reports to the manager imported in the same file.
        String newMgr = login("newmgr", JsonPath.read(body(result), "$.users[0].temporaryPassword"));
        mvc.perform(get(API + "/team").header("Authorization", bearer(newMgr)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("dev1"))
                .andExpect(jsonPath("$[0].designation").value("Senior Employee"));
        String dev1 = login("dev1", dev1Password);
        mvc.perform(get(API + "/me").header("Authorization", bearer(dev1)))
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void importIsAllOrNothing() throws Exception {
        String admin = login("admin1", TestData.PASSWORD);
        String csv = "username,email,group,reportingManager\n"
                + "okuser,okuser@example.com,EMPLOYEE,mgr1\n"
                + "emp1,dup@example.com,EMPLOYEE,\n"
                + "badgroup,bg@example.com,NOPE,\n"
                + "orphan,orphan@example.com,EMPLOYEE,nobody\n";
        mvc.perform(multipart(API + "/admin/users/import")
                        .file(new MockMultipartFile("file", "users.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.errors.length()").value(3));
        mvc.perform(get(API + "/admin/users").param("q", "okuser").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.totalElements").value(0));

        // Managers cannot import.
        mvc.perform(multipart(API + "/admin/users/import")
                        .file(new MockMultipartFile("file", "users.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(login("mgr1", TestData.PASSWORD))))
                .andExpect(status().isForbidden());
    }

    @Test
    void storageStatusAndMigrationNeedGitHub() throws Exception {
        String admin = login("admin1", TestData.PASSWORD);
        mvc.perform(get(API + "/admin/storage/status").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageType").value("local"))
                .andExpect(jsonPath("$.githubConfigured").value(false))
                .andExpect(jsonPath("$.counts.github").exists());
        // Tests run with local storage, so copying to GitHub is refused with a clear message.
        mvc.perform(post(API + "/admin/storage/migrate").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
        mvc.perform(get(API + "/admin/storage/status").header("Authorization", bearer(login("emp1", TestData.PASSWORD))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanSetEmployeeIdAndRoleOnUpdate() throws Exception {
        String admin = login("admin1", TestData.PASSWORD);
        int id = JsonPath.read(body(mvc.perform(get(API + "/admin/users").param("q", "emp2")
                .header("Authorization", bearer(admin))).andReturn()), "$.content[0].userId");
        mvc.perform(put(API + "/admin/users/" + id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeCode\":\"E9\",\"designation\":\"Team Lead\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeCode").value("E9"))
                .andExpect(jsonPath("$.designation").value("Team Lead"));
    }

    @Test
    void managerDeletesSomeoneWhoLeftTheirTeam() throws Exception {
        String mgr1 = login("mgr1", TestData.PASSWORD);
        String mgr2 = login("mgr2", TestData.PASSWORD);
        java.util.List<Integer> ids = JsonPath.read(body(mvc.perform(get(API + "/team")
                .header("Authorization", bearer(mgr1))).andReturn()), "$[?(@.username == 'emp1')].userId");
        int emp1 = ids.get(0);

        // Only their own manager may delete them.
        mvc.perform(delete(API + "/team/" + emp1).header("Authorization", bearer(mgr2)))
                .andExpect(status().isForbidden());
        mvc.perform(delete(API + "/team/" + emp1).header("Authorization", bearer(mgr1)))
                .andExpect(status().isOk());

        mvc.perform(get(API + "/team").header("Authorization", bearer(mgr1)))
                .andExpect(jsonPath("$[?(@.username == 'emp1')]").isEmpty());
        mvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"emp1\",\"password\":\"" + TestData.PASSWORD + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("deleted")));
    }

    @Test
    void adminDeletesAManagerAndTheirTeamMovesUpThenRestores() throws Exception {
        String admin = login("admin1", TestData.PASSWORD);
        String emp1 = login("emp1", TestData.PASSWORD);
        int mgrId = JsonPath.read(body(mvc.perform(get(API + "/admin/users").param("q", "mgr1")
                .header("Authorization", bearer(admin))).andReturn()), "$.content[0].userId");
        int adminId = JsonPath.read(body(mvc.perform(get(API + "/admin/users").param("q", "admin1")
                .header("Authorization", bearer(admin))).andReturn()), "$.content[0].userId");

        mvc.perform(delete(API + "/admin/users/" + adminId).header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());

        mvc.perform(delete(API + "/admin/users/" + mgrId).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportsMoved").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

        // Hidden from the normal list, shown in the deleted list; their team now reports to no one.
        mvc.perform(get(API + "/admin/users").param("q", "mgr1").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(API + "/admin/users").param("q", "mgr1").param("removed", "true").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(API + "/admin/users").param("q", "emp1").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.content[0].reportingManagerUsername").doesNotExist());
        mvc.perform(get(API + "/me").header("Authorization", bearer(emp1))).andExpect(status().isOk());

        mvc.perform(post(API + "/admin/users/" + mgrId + "/restore").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("A"));
        login("mgr1", TestData.PASSWORD);
    }

    @Test
    void managerBuildsATeamUnderATeamLeadAndApprovesTheirUploads() throws Exception {
        String mgr1 = login("mgr1", TestData.PASSWORD);
        String mgr2 = login("mgr2", TestData.PASSWORD);
        // A team lead (employee access) under mgr1, then a consultant who reports to the team lead.
        mvc.perform(post(API + "/team").header("Authorization", bearer(mgr1)).contentType(MediaType.APPLICATION_JSON)
                        .content(person("akshay.tl", null, "Team Lead")))
                .andExpect(status().isCreated());
        mvc.perform(get(API + "/team/managers").header("Authorization", bearer(mgr1)))
                .andExpect(jsonPath("$[?(@.username == 'akshay.tl')].inMyTeam").value(org.hamcrest.Matchers.contains(true)));
        mvc.perform(post(API + "/team").header("Authorization", bearer(mgr1)).contentType(MediaType.APPLICATION_JSON)
                        .content(person("rahul.c", "akshay.tl", "Consultant")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportingManagerUsername").value("akshay.tl"));
        // Another manager cannot add people under mgr1's team lead.
        mvc.perform(post(API + "/team").header("Authorization", bearer(mgr2)).contentType(MediaType.APPLICATION_JSON)
                        .content(person("x.y", "akshay.tl", "Employee")))
                .andExpect(status().isForbidden());

        // mgr1's team lists the consultant too.
        mvc.perform(get(API + "/team").header("Authorization", bearer(mgr1)))
                .andExpect(jsonPath("$[?(@.username == 'rahul.c')].reportingManagerUsername")
                        .value(org.hamcrest.Matchers.contains("akshay.tl")));

        // The consultant's upload skips the team lead (cannot approve) and goes to mgr1.
        String rahul = login("rahul.c", "Temp1234!");
        int id = JsonPath.read(body(mvc.perform(multipart(API + "/knowledge/submit")
                        .file(new MockMultipartFile("files", "a.txt", "text/plain", new byte[]{1}))
                        .param("title", "From the consultant").param("description", "d").param("keywords", "k")
                        .header("Authorization", bearer(rahul)))
                .andExpect(status().isCreated()).andReturn()), "$.knowledgeId");
        mvc.perform(post(API + "/knowledge/" + id + "/approve").header("Authorization", bearer(mgr2)))
                .andExpect(status().is4xxClientError());
        mvc.perform(post(API + "/knowledge/" + id + "/approve").header("Authorization", bearer(mgr1)))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // Nobody can report to someone in their own team.
        List<Integer> ids = JsonPath.read(body(mvc.perform(get(API + "/team")
                .header("Authorization", bearer(mgr1))).andReturn()), "$[?(@.username == 'akshay.tl')].userId");
        mvc.perform(put(API + "/team/" + ids.get(0)).header("Authorization", bearer(mgr1))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reportingManagerUsername\":\"rahul.c\"}"))
                .andExpect(status().isBadRequest());

        // Employees are never offered as "Reports to"; team leads are.
        mvc.perform(get(API + "/team/managers").header("Authorization", bearer(mgr1)))
                .andExpect(jsonPath("$[?(@.username == 'rahul.c')]").isEmpty())
                .andExpect(jsonPath("$[?(@.username == 'emp1')]").isEmpty());

        // Making rahul a Manager while he reports to a Team Lead is refused; under mgr1 it works.
        List<Integer> rahulIds = JsonPath.read(body(mvc.perform(get(API + "/team")
                .header("Authorization", bearer(mgr1))).andReturn()), "$[?(@.username == 'rahul.c')].userId");
        mvc.perform(put(API + "/team/" + rahulIds.get(0)).header("Authorization", bearer(mgr1))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"designation\":\"Manager\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(API + "/team/" + rahulIds.get(0)).header("Authorization", bearer(mgr1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designation\":\"Manager\",\"reportingManagerUsername\":\"akshay.tl\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(API + "/team/" + rahulIds.get(0)).header("Authorization", bearer(mgr1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designation\":\"Manager\",\"reportingManagerUsername\":\"mgr1\"}"))
                .andExpect(status().isOk());
    }

    private static String person(String username, String reportsTo, String designation) {
        return "{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"Temp1234!\""
                + ",\"designation\":\"" + designation + "\""
                + (reportsTo == null ? "" : ",\"reportingManagerUsername\":\"" + reportsTo + "\"") + "}";
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(body(result), "$.token");
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
