package com.example.cube;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reporting-manager routing, "My team" and bookmarks. */
@SpringBootTest
@AutoConfigureMockMvc
class TeamRoutingBookmarkTest {

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
    void submissionGoesOnlyToTheReportingManager() throws Exception {
        String emp1 = login("emp1"); // reports to mgr1
        String mgr1 = login("mgr1");
        String mgr2 = login("mgr2");

        int id = submit(emp1, "Routing test item");
        mvc.perform(get(API + "/knowledge/" + id).header("Authorization", bearer(emp1)))
                .andExpect(jsonPath("$.assignedReviewerName").value("mgr1"));

        // mgr2 neither sees it in the queue nor can open or approve it
        mvc.perform(get(API + "/knowledge").param("status", "PENDING").param("size", "100").header("Authorization", bearer(mgr2)))
                .andExpect(jsonPath("$.content[?(@.knowledgeId == " + id + ")]").doesNotExist());
        mvc.perform(get(API + "/knowledge/" + id).header("Authorization", bearer(mgr2)))
                .andExpect(status().isNotFound());
        mvc.perform(post(API + "/knowledge/" + id + "/approve").header("Authorization", bearer(mgr2)))
                .andExpect(status().isNotFound());

        // mgr1 sees it as assigned to them and approves it
        mvc.perform(get(API + "/knowledge").param("status", "PENDING").param("size", "100").header("Authorization", bearer(mgr1)))
                .andExpect(jsonPath("$.content[?(@.knowledgeId == " + id + " && @.assignedToMe == true)]").exists());
        mvc.perform(post(API + "/knowledge/" + id + "/approve").header("Authorization", bearer(mgr1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void submissionWithoutManagerIsOpenToAllApprovers() throws Exception {
        String emp4 = login("emp4"); // no reporting manager
        String mgr2 = login("mgr2");
        int id = submit(emp4, "Unassigned item");

        mvc.perform(get(API + "/knowledge").param("status", "PENDING").param("size", "100").header("Authorization", bearer(mgr2)))
                .andExpect(jsonPath("$.content[?(@.knowledgeId == " + id + " && @.assignedToMe == false)]").exists());
        mvc.perform(post(API + "/knowledge/" + id + "/approve").header("Authorization", bearer(mgr2)))
                .andExpect(status().isOk());
    }

    @Test
    void managerCreatesAndMovesTeamMembers() throws Exception {
        String mgr1 = login("mgr1");
        String mgr2 = login("mgr2");
        String emp1 = login("emp1");

        // Employees cannot manage a team
        mvc.perform(get(API + "/team").header("Authorization", bearer(emp1)))
                .andExpect(status().isForbidden());

        MvcResult created = mvc.perform(post(API + "/team").header("Authorization", bearer(mgr1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"teamnew\",\"email\":\"teamnew@example.com\",\"password\":\"Secret123\",\"firstName\":\"Team\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupCode").value("EMPLOYEE"))
                .andExpect(jsonPath("$.reportingManagerUsername").value("mgr1"))
                .andReturn();
        int userId = JsonPath.read(body(created), "$.userId");

        mvc.perform(get(API + "/team").header("Authorization", bearer(mgr1)))
                .andExpect(jsonPath("$[?(@.username == 'teamnew')]").exists());
        mvc.perform(get(API + "/team/managers").header("Authorization", bearer(mgr1)))
                .andExpect(jsonPath("$[?(@.username == 'mgr2')]").exists());

        // The new employee submits; it waits for mgr1
        String newbie = login("teamnew", "Secret123");
        int pending = submit(newbie, "Waiting for my manager");

        // mgr2 cannot edit someone else's report
        mvc.perform(put(API + "/team/" + userId).header("Authorization", bearer(mgr2))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"X\"}"))
                .andExpect(status().isForbidden());

        // mgr1 moves the employee to mgr2: the waiting item moves too
        mvc.perform(put(API + "/team/" + userId).header("Authorization", bearer(mgr1))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reportingManagerUsername\":\"mgr2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportingManagerUsername").value("mgr2"));
        mvc.perform(get(API + "/knowledge/" + pending).header("Authorization", bearer(mgr1)))
                .andExpect(status().isNotFound());
        mvc.perform(post(API + "/knowledge/" + pending + "/approve").header("Authorization", bearer(mgr2)))
                .andExpect(status().isOk());

        // A manager may pick themselves, someone in their own team or another manager - not someone else's employee
        mvc.perform(put(API + "/team/" + userId).header("Authorization", bearer(mgr2))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reportingManagerUsername\":\"emp1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void bookmarksPublishedItems() throws Exception {
        String mgr1 = login("mgr1");
        String emp2 = login("emp2");
        int id = submit(mgr1, "Bookmark me"); // managers publish directly

        mvc.perform(put(API + "/knowledge/" + id + "/bookmark").header("Authorization", bearer(emp2)))
                .andExpect(status().isNoContent());
        mvc.perform(get(API + "/knowledge/bookmarks").header("Authorization", bearer(emp2)))
                .andExpect(jsonPath("$.content[?(@.knowledgeId == " + id + " && @.bookmarked == true)]").exists());
        mvc.perform(get(API + "/knowledge/" + id).header("Authorization", bearer(emp2)))
                .andExpect(jsonPath("$.bookmarked").value(true));

        mvc.perform(delete(API + "/knowledge/" + id + "/bookmark").header("Authorization", bearer(emp2)))
                .andExpect(status().isNoContent());
        mvc.perform(get(API + "/knowledge/bookmarks").header("Authorization", bearer(emp2)))
                .andExpect(jsonPath("$.content[?(@.knowledgeId == " + id + ")]").doesNotExist());

        // Pending items cannot be bookmarked
        int pending = submit(login("emp1"), "Not yet published");
        mvc.perform(put(API + "/knowledge/" + pending + "/bookmark").header("Authorization", bearer(mgr1)))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ helpers

    private int submit(String token, String title) throws Exception {
        MvcResult result = mvc.perform(multipart(API + "/knowledge/submit")
                        .file(new org.springframework.mock.web.MockMultipartFile("files", "doc.txt", "text/plain", new byte[]{1}))
                        .param("title", title).param("description", "Some details").param("keywords", "general")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(body(result), "$.knowledgeId");
    }

    private String login(String username) throws Exception {
        return login(username, TestData.PASSWORD);
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
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
