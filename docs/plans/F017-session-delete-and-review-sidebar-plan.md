# F017 Session Delete and Review Sidebar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add confirmed hard-delete for research sessions and clarify the current-answer review sidebar semantics.

**Architecture:** The backend owns destructive session deletion through a project-scoped `DELETE` endpoint and explicit repository cleanup. The frontend adds a compact session action surface, calls the delete endpoint after confirmation, and updates local state through tested model helpers. UI copy changes clarify that process hits, final evidence, candidates, and confirmed knowledge have different lifecycles.

**Tech Stack:** Spring Boot MVC, JdbcTemplate, PostgreSQL/Flyway schema already present, static HTML/CSS/ES modules, Node ESM tests, Maven/JUnit/MockMvc.

---

## File Structure

- Modify `src/main/java/com/researchassistant/project/ProjectController.java`: add `DELETE /api/projects/{projectId}/sessions/{sessionId}`.
- Modify `src/main/java/com/researchassistant/project/ProjectRepository.java`: add `deleteSession(projectId, sessionId)` with explicit cleanup.
- Modify `src/test/java/com/researchassistant/project/ProjectControllerTest.java`: add focused deletion tests.
- Modify `src/main/resources/static/js/workbench-model.js`: add URL builder and local deletion state helper.
- Modify `src/main/resources/static/js/workbench-app.js`: wire session delete confirmation, endpoint call, render session action controls, and revise sidebar/process copy.
- Modify `src/main/resources/static/tests/workbench-model.test.mjs`: add model tests for deletion helper and URL builder.
- Modify `src/main/resources/static/index.html`: revise sidebar accessible labels and visible section headings.
- Modify `src/main/resources/static/css/workbench.css`: support compact session actions without layout overlap.
- Update Harness docs and Evidence after verification.

### Task 1: Backend Delete Contract

**Files:**
- Modify: `src/test/java/com/researchassistant/project/ProjectControllerTest.java`
- Modify: `src/main/java/com/researchassistant/project/ProjectController.java`
- Modify: `src/main/java/com/researchassistant/project/ProjectRepository.java`

- [ ] **Step 1: Write failing controller tests**

Add tests to `ProjectControllerTest`:

```java
@Test
void deletesSessionAndRemovesItFromProjectList() throws Exception {
    String projectId = createProject("Project");
    String sessionId = createSession(projectId, "Disposable session");
    createSession(projectId, "Remaining session");

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/projects/{projectId}/sessions/{sessionId}",
                    projectId,
                    sessionId
            ))
            .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/projects/{projectId}/sessions", projectId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.not(hasItem(sessionId))));
}

@Test
void deleteSessionRejectsCrossProjectSession() throws Exception {
    String projectA = createProject("Project A");
    String projectB = createProject("Project B");
    String sessionB = createSession(projectB, "Other project session");

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/projects/{projectId}/sessions/{sessionId}",
                    projectA,
                    sessionB
            ))
            .andExpect(status().isNotFound());

    mockMvc.perform(get("/api/projects/{projectId}/sessions", projectB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem(sessionB)));
}
```

- [ ] **Step 2: Run tests to verify RED**

Run:

```powershell
& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectControllerTest' test
```

Expected: fail with no handler for `DELETE /api/projects/{projectId}/sessions/{sessionId}`.

- [ ] **Step 3: Implement minimal backend endpoint**

Add `@DeleteMapping` import and controller method:

```java
@DeleteMapping("/{projectId}/sessions/{sessionId}")
public ResponseEntity<Void> deleteSession(
        @PathVariable String projectId,
        @PathVariable String sessionId
) {
    if (projectRepository.findProject(projectId).isEmpty()) {
        return ResponseEntity.notFound().build();
    }
    boolean deleted = projectRepository.deleteSession(projectId, sessionId);
    return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
}
```

Add repository method:

```java
public boolean deleteSession(String projectId, String sessionId) {
    if (findSession(projectId, sessionId).isEmpty()) {
        return false;
    }
    jdbcTemplate.update("""
            delete from stream_event_record
            where project_id = ?
              and session_id = ?
            """, projectId, sessionId);
    jdbcTemplate.update("""
            delete from knowledge_candidate
            where project_id = ?
              and session_id = ?
            """, projectId, sessionId);
    jdbcTemplate.update("""
            delete from assistant_answer
            where project_id = ?
              and session_id = ?
            """, projectId, sessionId);
    jdbcTemplate.update("""
            delete from retrieval_trace
            where session_id in (
                select id from chat_session where session_key = ?
            )
            """, sessionId);
    jdbcTemplate.update("""
            delete from memory_entry
            where session_id in (
                select id from chat_session where session_key = ?
            )
            """, sessionId);
    jdbcTemplate.update("""
            delete from chat_session
            where session_key = ?
            """, sessionId);
    int deleted = jdbcTemplate.update("""
            delete from research_session
            where project_id = ?
              and id = ?
            """, projectId, sessionId);
    return deleted > 0;
}
```

- [ ] **Step 4: Run tests to verify GREEN**

Run the same Maven command. Expected: `ProjectControllerTest` passes.

### Task 2: Frontend Session Delete State

**Files:**
- Modify: `src/main/resources/static/tests/workbench-model.test.mjs`
- Modify: `src/main/resources/static/js/workbench-model.js`

- [ ] **Step 1: Write failing frontend model tests**

Add tests:

```javascript
test("buildProjectSessionDeleteUrl builds the project scoped delete endpoint", () => {
  assert.equal(
    buildProjectSessionDeleteUrl({ projectId: "p 1", sessionId: "s/2" }),
    "/api/projects/p%201/sessions/s%2F2"
  );
});

test("deleteSessionFromState removes active session and selects the next one", () => {
  const state = createWorkbenchState({
    activeProjectId: "p1",
    activeSessionId: "s1",
    sessions: [
      { id: "s1", title: "First" },
      { id: "s2", title: "Second" }
    ],
    messages: [{ id: "m1" }],
    evidenceSources: [{ id: "e1" }],
    candidates: [{ id: "c1", sessionId: "s1" }],
    activeAnswerContext: { answerId: "a1" }
  });

  const next = deleteSessionFromState(state, "s1");

  assert.equal(next.activeSessionId, "s2");
  assert.deepEqual(next.sessions.map((session) => session.id), ["s2"]);
  assert.deepEqual(next.messages, []);
  assert.deepEqual(next.evidenceSources, []);
  assert.deepEqual(next.activeAnswerContext, null);
});
```

Update imports in the test file for `buildProjectSessionDeleteUrl` and `deleteSessionFromState`.

- [ ] **Step 2: Run tests to verify RED**

Run:

```powershell
node --test src/main/resources/static/tests/workbench-model.test.mjs
```

Expected: fail because the two exports do not exist.

- [ ] **Step 3: Implement model helpers**

Add to `workbench-model.js`:

```javascript
export function buildProjectSessionDeleteUrl(context) {
    return `/api/projects/${encodeURIComponent(context.projectId)}/sessions/${encodeURIComponent(context.sessionId)}`;
}

export function deleteSessionFromState(state, sessionId) {
    const sessions = (state.sessions || []).filter((session) => session.id !== sessionId);
    const deletedActive = state.activeSessionId === sessionId;
    const activeSessionId = deletedActive ? sessions[0]?.id || null : state.activeSessionId || null;
    return {
        ...state,
        sessions,
        activeSessionId,
        activeAnswerContext: deletedActive ? null : state.activeAnswerContext || null,
        currentAnswer: deletedActive
                ? { answerId: null, text: "", status: "idle", evidenceState: null, outputMode: null, citationCount: 0 }
                : state.currentAnswer,
        messages: deletedActive ? [] : state.messages || [],
        evidenceSources: deletedActive ? [] : state.evidenceSources || [],
        candidates: deletedActive
                ? (state.candidates || []).filter((candidate) => candidate.sessionId !== sessionId)
                : state.candidates || [],
        agentTraces: deletedActive ? {} : state.agentTraces || {},
        processedEventIds: deletedActive ? [] : state.processedEventIds || []
    };
}
```

- [ ] **Step 4: Run tests to verify GREEN**

Run the same Node test command. Expected: pass.

### Task 3: Frontend Delete Interaction and Copy

**Files:**
- Modify: `src/main/resources/static/js/workbench-app.js`
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/css/workbench.css`

- [ ] **Step 1: Wire delete interaction**

Import `buildProjectSessionDeleteUrl` and `deleteSessionFromState`.

In the session list click handler, add delete trigger handling before selection:

```javascript
const deleteTrigger = event.target.closest("[data-session-delete]");
if (deleteTrigger) {
    void submitSessionDelete(deleteTrigger.dataset.sessionDelete);
    return;
}
```

Create:

```javascript
async function submitSessionDelete(sessionId) {
    const session = app.sessions.find((item) => item.id === sessionId);
    if (!session) {
        return;
    }
    const confirmed = window.confirm(`删除会话“${session.title || "未命名会话"}”？这会删除该会话的消息、回答、证据、候选和研究过程记录，无法撤销。`);
    if (!confirmed) {
        return;
    }
    if (!hasProjectApi()) {
        Object.assign(app, deleteSessionFromState(app, sessionId));
        app.statusMessage = "本地样例会话已删除。";
        render();
        return;
    }
    try {
        await deleteJson(buildProjectSessionDeleteUrl({ projectId: app.activeProjectId, sessionId }));
        Object.assign(app, deleteSessionFromState(app, sessionId));
        app.selectedDiagnosticId = null;
        app.retrievalDiagnostics = [];
        app.diagnosticsLoadState = "idle";
        app.statusMessage = "会话已删除。";
        if (app.activeSessionId) {
            await loadActiveSessionMessages();
        }
    } catch (error) {
        app.statusMessage = `删除会话失败：${error.message}`;
    }
    render();
}

async function deleteJson(url) {
    const response = await fetch(url, { method: "DELETE" });
    return readResponse(response);
}
```

- [ ] **Step 2: Render compact session actions**

In `renderSessions`, replace the single rename icon append with an actions container:

```javascript
const actions = document.createElement("div");
actions.className = "session-row-actions session-row-actions--compact";
actions.append(
        sessionIconButton(session.id, "rename", "重命名会话", "✎"),
        sessionIconButton(session.id, "delete", "删除会话", "×")
);
item.append(selectButton, actions);
```

Add helper:

```javascript
function sessionIconButton(sessionId, action, label, icon) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = action === "delete" ? "icon-button icon-button--danger" : "icon-button";
    button.dataset[action === "delete" ? "sessionDelete" : "sessionRename"] = sessionId;
    button.title = label;
    button.setAttribute("aria-label", label);
    button.textContent = icon;
    return button;
}
```

- [ ] **Step 3: Revise sidebar and process copy**

Change right sidebar labels in `index.html` to use `当前回答审阅区` for the aside `aria-label` and heading text if present.

In `renderSidebar`, change context line fallback to current-answer language.

In `renderEvidenceSources`, change empty copy to:

```javascript
emptyBlock("当前回答还没有最终引用。回答完成并完成证据评估后会显示。")
```

In `renderCandidates`, change empty copy to:

```javascript
emptyBlock("本轮尚未生成待确认候选。开启“提炼候选”后，回答完成时会出现在这里。")
```

In compact knowledge board empty states, use:

```javascript
emptyBlock("项目还没有确认知识。候选确认或手动新建后会进入知识工作区。")
```

In `renderResearchProcess`, change `证据与记忆` to `过程命中` and waiting copy to:

```javascript
"等待资料命中、联网补充或记忆召回事件。过程命中不等于最终引用。"
```

- [ ] **Step 4: CSS polish**

Add styles:

```css
.session-row-actions--compact {
    display: flex;
    gap: 6px;
    align-items: center;
    flex: 0 0 auto;
}

.icon-button--danger {
    color: var(--danger);
}

.icon-button--danger:hover {
    border-color: color-mix(in oklch, var(--danger) 35%, var(--line));
    background: color-mix(in oklch, var(--danger) 8%, var(--surface));
}
```

- [ ] **Step 5: Run frontend tests**

Run:

```powershell
node --test src/main/resources/static/tests/workbench-model.test.mjs
```

Expected: pass.

### Task 4: Verification and Harness Closeout

**Files:**
- Add or update: `docs/evidence/EV-016-f017-session-delete-and-review-sidebar.md`
- Modify: `docs/features/F017-session-delete-and-review-sidebar.md`
- Modify: `docs/BACKLOG.md`

- [ ] **Step 1: Run focused verification**

Run:

```powershell
& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectControllerTest' test
node --test src/main/resources/static/tests/workbench-model.test.mjs
python scripts/knowledge_check.py --root . --docs-path docs
```

Expected: all commands pass.

- [ ] **Step 2: Browser check**

Start or reuse the local dev server, open the workbench, and verify:

- Session row actions fit on desktop.
- Delete confirmation appears.
- Right sidebar copy distinguishes current answer review from full knowledge workspace.
- Research process says `过程命中`.

- [ ] **Step 3: Record Evidence**

Create `docs/evidence/EV-016-f017-session-delete-and-review-sidebar.md` with commands, results, and manual/browser notes.

- [ ] **Step 4: Close Feature status**

Update `F017` status to completed only if verification and browser check pass. Update `docs/BACKLOG.md` by moving F017 from active to recently completed or noting any residual blockers.
