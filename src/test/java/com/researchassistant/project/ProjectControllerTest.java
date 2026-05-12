package com.researchassistant.project;

import com.jayway.jsonpath.JsonPath;
import com.researchassistant.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ProjectControllerTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsListsAndFetchesProjects() throws Exception {
        String responseBody = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topic": "Agentic research workbench",
                                  "summary": "Grounded long-running research"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.topic").value("Agentic research workbench"))
                .andExpect(jsonPath("$.summary").value("Grounded long-running research"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.stats.sourceCount").value(0))
                .andExpect(jsonPath("$.stats.sessionCount").value(0))
                .andExpect(jsonPath("$.stats.knowledgeEntryCount").value(0))
                .andExpect(jsonPath("$.stats.candidateCount").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String projectId = JsonPath.read(responseBody, "$.id");

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(java.util.List.class)))
                .andExpect(jsonPath("$[*].id", hasItem(projectId)));

        mockMvc.perform(get("/api/projects/{projectId}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId))
                .andExpect(jsonPath("$.topic").value("Agentic research workbench"))
                .andExpect(jsonPath("$.stats.sourceCount").value(0));
    }

    @Test
    void createsAndListsSessionsUnderProject() throws Exception {
        String projectA = createProject("Project A");
        String projectB = createProject("Project B");

        String sessionA = createSession(projectA, "Evidence boundary design");
        String sessionB = createSession(projectB, "Other project session");

        mockMvc.perform(get("/api/projects/{projectId}/sessions", projectA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(java.util.List.class)))
                .andExpect(jsonPath("$[*].id", hasItem(sessionA)))
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.not(hasItem(sessionB))))
                .andExpect(jsonPath("$[?(@.id == '" + sessionA + "')].projectId", hasItem(projectA)))
                .andExpect(jsonPath("$[?(@.id == '" + sessionA + "')].title", hasItem("Evidence boundary design")))
                .andExpect(jsonPath("$[?(@.id == '" + sessionA + "')].status", hasItem("continue")))
                .andExpect(jsonPath("$[?(@.id == '" + sessionA + "')].lastMessageAt", hasItem(nullValue())));
    }

    @Test
    void renamesSessionUnderProject() throws Exception {
        String projectId = createProject("Project");
        String sessionId = createSession(projectId, "Initial title");

        mockMvc.perform(patch("/api/projects/{projectId}/sessions/{sessionId}", projectId, sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Transformer literature review"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId))
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.title").value("Transformer literature review"))
                .andExpect(jsonPath("$.status").value("continue"));

        mockMvc.perform(get("/api/projects/{projectId}/sessions", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + sessionId + "')].title", hasItem("Transformer literature review")));
    }

    private String createProject(String topic) throws Exception {
        String responseBody = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topic": "%s",
                                  "summary": "summary"
                                }
                                """.formatted(topic)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(responseBody, "$.id");
    }

    private String createSession(String projectId, String title) throws Exception {
        String responseBody = mockMvc.perform(post("/api/projects/{projectId}/sessions", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s"
                                }
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.status").value("continue"))
                .andExpect(jsonPath("$.lastMessageAt").value(nullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(responseBody, "$.id");
    }
}
