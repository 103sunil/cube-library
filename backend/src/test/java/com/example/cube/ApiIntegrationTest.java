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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

    private static final String API = "/api/v1";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TestData testData;

    @BeforeEach
    void seed() {
        testData.seed();
    }

    // ------------------------------------------------------------------ auth

    @Test
    void loginReturnsTokenAndPermissions() throws Exception {
        mvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"emp1\",\"password\":\"" + TestData.PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.groupCode").value("EMPLOYEE"));

        String token = login("emp1");
        mvc.perform(get(API + "/me/permissions").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.hasItems(
                        "KNOWLEDGE:VIEW", "KNOWLEDGE:SEARCH", "KNOWLEDGE:DOWNLOAD", "KNOWLEDGE:CREATE")))
                .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("KNOWLEDGE:APPROVE"))))
                .andExpect(jsonPath("$.modules.KNOWLEDGE").isArray());

        mvc.perform(get(API + "/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("emp1"));
    }

    @Test
    void badLoginsReturnUniformErrors() throws Exception {
        mvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"emp2\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andExpect(jsonPath("$.path").value(API + "/auth/login"));

        mvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"inactive1\",\"password\":\"" + TestData.PASSWORD + "\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"emp2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        mvc.perform(get(API + "/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void loginIsLockedAfterRepeatedFailures() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"emp3\",\"password\":\"bad\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"emp3\",\"password\":\"" + TestData.PASSWORD + "\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void logoutRevokesToken() throws Exception {
        String token = login("emp1");
        mvc.perform(post(API + "/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mvc.perform(get(API + "/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ knowledge workflow

    @Test
    void employeeSubmissionIsReviewedDownloadedAndPublished() throws Exception {
        String emp = login("emp1");
        String other = login("emp2");
        String mgr = login("mgr1");

        byte[] fileBytes = "quarterly figures".getBytes(StandardCharsets.UTF_8);
        MvcResult created = mvc.perform(submitRequest("/knowledge/submit", "Expense policy zebra", "How to claim expenses",
                        new String[]{"finance", "expenses"},
                        new MockMultipartFile("files", "report.pdf", "application/pdf", fileBytes),
                        new MockMultipartFile("files", "notes.txt", "text/plain", "n".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(emp)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attachmentCount").value(2))
                .andExpect(jsonPath("$.keywords").value(org.hamcrest.Matchers.containsInAnyOrder("finance", "expenses")))
                .andReturn();
        int id = JsonPath.read(body(created), "$.knowledgeId");
        int attachmentId = JsonPath.read(body(created), "$.attachments[0].attachmentId");

        // Employee sees it under "mine"; another employee cannot see it at all.
        mvc.perform(get(API + "/knowledge/mine").header("Authorization", bearer(emp)))
                .andExpect(jsonPath("$.content[?(@.knowledgeId == " + id + ")]").exists());
        mvc.perform(get(API + "/knowledge/" + id).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        mvc.perform(get(API + "/attachments/" + attachmentId + "/download").header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());

        // Employees cannot use the review queue.
        mvc.perform(get(API + "/knowledge").param("status", "PENDING").header("Authorization", bearer(emp)))
                .andExpect(status().isForbidden());

        // Manager sees it in the queue and CAN download / preview the employee's attachment (the reported bug).
        mvc.perform(get(API + "/knowledge").param("status", "PENDING").header("Authorization", bearer(mgr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.knowledgeId == " + id + ")]").exists());
        mvc.perform(get(API + "/attachments/" + attachmentId + "/download").header("Authorization", bearer(mgr)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(fileBytes))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));
        mvc.perform(get(API + "/attachments/" + attachmentId + "/preview").header("Authorization", bearer(mgr)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")));
        mvc.perform(get(API + "/knowledge/" + id).header("Authorization", bearer(mgr)))
                .andExpect(jsonPath("$.actions.canApprove").value(true))
                .andExpect(jsonPath("$.actions.canDownload").value(true));

        // Employee cannot approve.
        mvc.perform(post(API + "/knowledge/" + id + "/approve").header("Authorization", bearer(emp)))
                .andExpect(status().isForbidden());

        mvc.perform(post(API + "/knowledge/" + id + "/approve").header("Authorization", bearer(mgr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.reviewedBy").value("mgr1"));

        // Now visible, searchable and downloadable by everyone with VIEW/DOWNLOAD.
        mvc.perform(get(API + "/knowledge/published").header("Authorization", bearer(other)))
                .andExpect(jsonPath("$.content[?(@.knowledgeId == " + id + ")]").exists())
                .andExpect(jsonPath("$.content[?(@.knowledgeId == " + id + ")].summary")
                        .value(org.hamcrest.Matchers.hasItem("How to claim expenses")));
        mvc.perform(get(API + "/knowledge/search").param("q", "ZEBRA expenses").header("Authorization", bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].knowledgeId").value(id));
        mvc.perform(get(API + "/attachments/" + attachmentId + "/download").header("Authorization", bearer(other)))
                .andExpect(status().isOk());

        // Employees can neither edit nor delete published items.
        mvc.perform(put(API + "/knowledge/" + id).header("Authorization", bearer(emp))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\",\"description\":\"y\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(API + "/knowledge/" + id).header("Authorization", bearer(emp)))
                .andExpect(status().isForbidden());

        // Archive hides it from employees; restore brings it back.
        mvc.perform(post(API + "/knowledge/" + id + "/archive").header("Authorization", bearer(mgr)))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        mvc.perform(get(API + "/knowledge/" + id).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        mvc.perform(post(API + "/knowledge/" + id + "/restore").header("Authorization", bearer(mgr)))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // Manager delete cascades keywords, attachments and stored files.
        mvc.perform(delete(API + "/knowledge/" + id).header("Authorization", bearer(mgr)))
                .andExpect(status().isNoContent());
        mvc.perform(get(API + "/knowledge/" + id).header("Authorization", bearer(mgr)))
                .andExpect(status().isNotFound());
        mvc.perform(get(API + "/attachments/" + attachmentId + "/download").header("Authorization", bearer(mgr)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectedItemCanBeEditedAndResubmittedByOwner() throws Exception {
        String emp = login("emp1");
        String other = login("emp2");
        String mgr = login("mgr1");

        int id = JsonPath.read(body(mvc.perform(submitRequest("/knowledge/submit", "VPN setup", "steps",
                        new String[]{"it"}).header("Authorization", bearer(emp)))
                .andExpect(status().isCreated()).andReturn()), "$.knowledgeId");

        mvc.perform(post(API + "/knowledge/" + id + "/reject").header("Authorization", bearer(mgr))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
        mvc.perform(post(API + "/knowledge/" + id + "/reject").header("Authorization", bearer(mgr))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Add screenshots\"}"))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("Add screenshots"));

        // Only the owner may edit; others don't even see it.
        mvc.perform(put(API + "/knowledge/" + id).header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\",\"description\":\"y\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(put(API + "/knowledge/" + id).header("Authorization", bearer(emp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"VPN setup v2\",\"description\":\"steps with screenshots\",\"keywords\":[\"it\",\"vpn\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.keywords.length()").value(2))
                .andExpect(jsonPath("$.actions.canSubmit").value(true));

        // Owner can add an attachment while rejected.
        mvc.perform(multipart(API + "/knowledge/" + id + "/attachments")
                        .file(new MockMultipartFile("files", "shot.png", "image/png", new byte[]{1, 2, 3}))
                        .header("Authorization", bearer(emp)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].fileName").value("shot.png"));

        mvc.perform(post(API + "/knowledge/" + id + "/submit").header("Authorization", bearer(emp)))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.rejectReason").doesNotExist());

        // Cannot resubmit while pending.
        mvc.perform(post(API + "/knowledge/" + id + "/submit").header("Authorization", bearer(emp)))
                .andExpect(status().isConflict());

        mvc.perform(post(API + "/knowledge/" + id + "/approve").header("Authorization", bearer(mgr)))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void managerSubmissionAutoPublishes() throws Exception {
        String mgr = login("mgr1");
        mvc.perform(submitRequest("/knowledge/submit", "Onboarding checklist", "day one", new String[]{"hr, onboarding"})
                        .header("Authorization", bearer(mgr)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.keywords.length()").value(2));
    }

    @Test
    void draftsCanBeSavedThenSubmitted() throws Exception {
        String emp = login("emp1");
        int id = JsonPath.read(body(mvc.perform(submitRequest("/knowledge/draft", "Half done", null, new String[0])
                        .header("Authorization", bearer(emp)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn()), "$.knowledgeId");

        // A description is needed before it can be submitted.
        mvc.perform(post(API + "/knowledge/" + id + "/submit").header("Authorization", bearer(emp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Description is required"));

        mvc.perform(put(API + "/knowledge/" + id).header("Authorization", bearer(emp))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Done\",\"description\":\"full text\"}"))
                .andExpect(status().isOk());
        // Keywords and an attachment are also mandatory before submitting.
        mvc.perform(post(API + "/knowledge/" + id + "/submit").header("Authorization", bearer(emp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add at least one keyword"));
        mvc.perform(put(API + "/knowledge/" + id).header("Authorization", bearer(emp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Done\",\"description\":\"full text\",\"keywords\":[\"howto\"]}"))
                .andExpect(status().isOk());
        mvc.perform(post(API + "/knowledge/" + id + "/submit").header("Authorization", bearer(emp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Attach at least one file"));
        int attachmentId = JsonPath.read(body(mvc.perform(multipart(API + "/knowledge/" + id + "/attachments")
                        .file(new MockMultipartFile("files", "guide.txt", "text/plain", new byte[]{1}))
                        .header("Authorization", bearer(emp)))
                .andExpect(status().isCreated()).andReturn()), "$[0].attachmentId");
        mvc.perform(post(API + "/knowledge/" + id + "/submit").header("Authorization", bearer(emp)))
                .andExpect(jsonPath("$.status").value("PENDING"));
        // The only attachment of a submitted item cannot be removed.
        mvc.perform(delete(API + "/attachments/" + attachmentId).header("Authorization", bearer(emp)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submittingRequiresKeywordsAndAttachments() throws Exception {
        String emp = login("emp1");
        mvc.perform(multipart(API + "/knowledge/submit").param("title", "No extras").param("description", "d")
                        .param("keywords", "kw").header("Authorization", bearer(emp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Attach at least one file"));
        mvc.perform(multipart(API + "/knowledge/submit")
                        .file(new MockMultipartFile("files", "a.txt", "text/plain", new byte[]{1}))
                        .param("title", "No extras").param("description", "d")
                        .header("Authorization", bearer(emp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add at least one keyword"));
    }

    @Test
    void uploadValidation() throws Exception {
        String emp = login("emp1");

        mvc.perform(submitRequest("/knowledge/submit", "Big file", "desc", new String[0],
                        new MockMultipartFile("files", "big.bin", "application/octet-stream", new byte[10 * 1024 * 1024 + 1]))
                        .header("Authorization", bearer(emp)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("10MB")));

        mvc.perform(submitRequest("/knowledge/submit", " ", "desc", new String[0]).header("Authorization", bearer(emp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Title is required"));

        mvc.perform(get(API + "/knowledge").param("status", "BOGUS").header("Authorization", bearer(login("mgr1"))))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ admin

    @Test
    void adminManagesUsersAndOverrides() throws Exception {
        String mgr = login("admin1"); // ADMIN group: AUTH:MANAGE_* only
        String emp = login("emp1");
        String manager = login("mgr1");

        mvc.perform(get(API + "/admin/users").header("Authorization", bearer(emp)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        // Managers review knowledge but do not administer users (as in CUBEDB).
        mvc.perform(get(API + "/admin/users").header("Authorization", bearer(manager)))
                .andExpect(status().isForbidden());
        // Administrators don't see knowledge unless granted.
        mvc.perform(get(API + "/knowledge/published").header("Authorization", bearer(mgr)))
                .andExpect(status().isForbidden());
        mvc.perform(get(API + "/admin/access-codes").header("Authorization", bearer(mgr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.permission == 'KNOWLEDGE:CREATE')]").exists());

        MvcResult created = mvc.perform(post(API + "/admin/users").header("Authorization", bearer(mgr))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newbie\",\"email\":\"newbie@example.com\",\"password\":\"Secret123\","
                                + "\"firstName\":\"New\",\"groupCode\":\"EMPLOYEE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupCode").value("EMPLOYEE"))
                .andReturn();
        int userId = JsonPath.read(body(created), "$.userId");

        mvc.perform(post(API + "/admin/users").header("Authorization", bearer(mgr))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"NEWBIE\",\"email\":\"other@example.com\",\"password\":\"Secret123\",\"groupCode\":\"EMPLOYEE\"}"))
                .andExpect(status().isConflict());

        // Temporary approver right via override, and an explicit deny of DOWNLOAD.
        mvc.perform(put(API + "/admin/users/" + userId + "/overrides").header("Authorization", bearer(mgr))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moduleCode\":\"KNOWLEDGE\",\"accessCode\":\"APPROVE\",\"allowed\":true,\"expiryDate\":\"2999-12-31\"}"))
                .andExpect(status().isOk());
        mvc.perform(put(API + "/admin/users/" + userId + "/overrides").header("Authorization", bearer(mgr))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moduleCode\":\"KNOWLEDGE\",\"accessCode\":\"DOWNLOAD\",\"allowed\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        MvcResult perms = mvc.perform(get(API + "/admin/users/" + userId + "/permissions").header("Authorization", bearer(mgr)))
                .andExpect(status().isOk()).andReturn();
        List<String> list = JsonPath.read(body(perms), "$");
        assertThat(list).contains("KNOWLEDGE:APPROVE", "KNOWLEDGE:VIEW").doesNotContain("KNOWLEDGE:DOWNLOAD");

        // Deactivated users cannot log in.
        mvc.perform(put(API + "/admin/users/" + userId).header("Authorization", bearer(mgr))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"I\"}"))
                .andExpect(jsonPath("$.status").value("I"));
        mvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newbie\",\"password\":\"Secret123\"}"))
                .andExpect(status().isForbidden());

        // Groups in use and system codes are protected.
        mvc.perform(get(API + "/admin/groups").header("Authorization", bearer(mgr)))
                .andExpect(status().isOk());
        mvc.perform(get(API + "/admin/audit").header("Authorization", bearer(mgr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    // ------------------------------------------------------------------ helpers

    private MockMultipartHttpServletRequestBuilder submitRequest(String path, String title, String description,
                                                                 String[] keywords, MockMultipartFile... files) {
        MockMultipartHttpServletRequestBuilder builder = multipart(API + path);
        boolean submit = path.endsWith("/submit");
        for (MockMultipartFile f : files) {
            builder.file(f);
        }
        // Submitted items need a file and a keyword; tests that don't care get defaults.
        if (submit && files.length == 0) {
            builder.file(new MockMultipartFile("files", "doc.txt", "text/plain", new byte[]{1}));
        }
        if (submit && keywords.length == 0) {
            keywords = new String[]{"general"};
        }
        if (title != null) {
            builder.param("title", title);
        }
        if (description != null) {
            builder.param("description", description);
        }
        if (keywords.length > 0) {
            builder.param("keywords", keywords);
        }
        return builder;
    }

    private String login(String username) throws Exception {
        MvcResult result = mvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + TestData.PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(body(result), "$.token");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
