# F002 下一代研究工作台 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将当前单文档问答 Demo 重构为项目级长期研究工作台，并让项目、资料、对话、事件、证据、候选、知识板和反馈具备可测试合同。

**Architecture:** 以 `Project` 为最高层领域对象，新增 project/workbench/event/candidate/knowledge 边界，保留现有 ingest、rag、memory、orchestrator、feedback 中可复用的局部能力。后端用 Spring Boot + PostgreSQL/Flyway 承载同步 API，用 Redis Stream 抽象记录过程事件，并通过 SSE 投影给静态三栏工作台。

**Tech Stack:** Java 17, Spring Boot 3.5.13, Spring MVC, JDBC, Flyway, PostgreSQL/pgvector, Redis Stream abstraction, Server-Sent Events, Maven, JUnit 5, static HTML/CSS/JS.

---

## 事实源与护栏

- 规格：[../specs/F002-next-generation-research-workbench-spec.md](../specs/F002-next-generation-research-workbench-spec.md)
- Feature：[../features/F002-next-generation-research-workbench.md](../features/F002-next-generation-research-workbench.md)
- ADR：[../decisions/ADR-002-rebuild-around-project-workbench.md](../decisions/ADR-002-rebuild-around-project-workbench.md)
- UI 规格：[../product/research-workbench-ui-interaction-spec.md](../product/research-workbench-ui-interaction-spec.md)

不可违背的边界：

- 不以旧 `sessionKey + documentId` 作为新架构中心。
- 不让 Agent 直接写入已确认知识板。
- 不把联网补充伪装成本地论文证据。
- 不让 L3 记忆直接替代当前回答证据边界。
- Redis Stream 若不实现，编码前必须新增 ADR 明确否决和替代机制。

## 文件结构规划

### 新增后端包

- `src/main/java/com/researchassistant/project/`：项目、会话、资料摘要的项目级 API 与仓储。
- `src/main/java/com/researchassistant/workbench/`：工作台聚合查询和三栏页面所需 view model。
- `src/main/java/com/researchassistant/events/`：事件 envelope、事件类型、Redis Stream port、内存测试实现、SSE 投影。
- `src/main/java/com/researchassistant/candidates/`：知识候选生成后的状态机与确认 API。
- `src/main/java/com/researchassistant/knowledge/`：知识板分区、条目和用户确认写入。

### 修改现有后端包

- `src/main/java/com/researchassistant/ingest/`：把资料接入从单 document 扩展到 project-scoped source，并发布 `source.status.changed`。
- `src/main/java/com/researchassistant/orchestrator/`：让 Supervisor 接受 `projectId/sessionId`，发布 Agent 过程事件。
- `src/main/java/com/researchassistant/rag/`：保留 Paper RAG，补齐 evidence trace 与 feedbackScore 使用边界。
- `src/main/java/com/researchassistant/evidence/`：输出 `SUFFICIENT/WEAK/NONE` 与 `LOCAL_EVIDENCE/LOCAL_WEAK_EVIDENCE/WEB_SUPPLEMENT/REFUSAL`。
- `src/main/java/com/researchassistant/feedback/`：把 answer feedback 回流到关联 evidence chunk。
- `src/main/java/com/researchassistant/memory/`：明确 L1/L2/L3 写入触发器和 `lastDepositedTurnId`。
- `src/main/resources/db/migration/`：新增 V5 或后续迁移，建立 F002 项目级表。

### 修改前端文件

- `src/main/resources/static/index.html`：三栏研究工作台 DOM。
- `src/main/resources/static/css/workbench.css`：三栏响应式布局和状态样式。
- `src/main/resources/static/js/workbench-model.js`：项目、会话、资料、回答、候选、知识板状态模型。
- `src/main/resources/static/js/workbench-app.js`：API 调用、SSE 消费、交互流程。
- `src/main/resources/static/contracts/workbench-api-contract.md`：从旧静态 UI 合同升级为 F002 API/SSE 摘要。

### 新增测试文件

- `src/test/java/com/researchassistant/project/ProjectControllerTest.java`
- `src/test/java/com/researchassistant/project/ProjectRepositoryTest.java`
- `src/test/java/com/researchassistant/events/StreamEventEnvelopeTest.java`
- `src/test/java/com/researchassistant/events/SseProjectionControllerTest.java`
- `src/test/java/com/researchassistant/ingest/ProjectSourceStatusMachineTest.java`
- `src/test/java/com/researchassistant/orchestrator/ProjectRunEventFlowTest.java`
- `src/test/java/com/researchassistant/evidence/ProjectEvidenceBoundaryTest.java`
- `src/test/java/com/researchassistant/candidates/KnowledgeCandidateControllerTest.java`
- `src/test/java/com/researchassistant/knowledge/KnowledgeBoardControllerTest.java`
- `src/test/java/com/researchassistant/feedback/ProjectFeedbackServiceTest.java`
- `src/main/resources/static/tests/f002-workbench-model.test.mjs`

## 切片总览

| 切片 | 主要产物 | 可并行性 |
| --- | --- | --- |
| 1. 项目级数据模型 | Project、Session、Source、Answer、Evidence、Candidate、KnowledgeEntry 表与 API | 先行切片 |
| 2. 事件骨干 | Redis Stream port、事件 envelope、SSE 投影 | 可与切片 1 部分并行，但依赖 run/project ID 约定 |
| 3. 资料状态机 | project-scoped source import、失败阶段、重试、事件发布 | 依赖切片 1、2 的核心合同 |
| 4. Agent 编排事件 | Supervisor plan-execute 事件、answer delta、run lifecycle | 依赖切片 2 |
| 5. 检索与证据边界 | L3 recall/Paper RAG/Web supplement 分层、证据状态 | 依赖切片 1、4 |
| 6. 候选与知识板 | 候选确认、编辑确认、知识板条目写入 | 依赖切片 1、4、5 |
| 7. 反馈闭环 | answer feedback 到 chunk feedbackScore | 依赖切片 1、5 |
| 8. 三栏前端与验收 | UI 状态、SSE 消费、浏览器验证、Evidence | 收束切片 |

## Task 1: 项目级数据模型与基础 API

**Files:**
- Create: `src/main/resources/db/migration/V5__f002_project_workbench_schema.sql`
- Create: `src/main/java/com/researchassistant/project/ProjectRecord.java`
- Create: `src/main/java/com/researchassistant/project/ResearchSessionRecord.java`
- Create: `src/main/java/com/researchassistant/project/ProjectRepository.java`
- Create: `src/main/java/com/researchassistant/project/ProjectController.java`
- Create: `src/test/java/com/researchassistant/project/ProjectRepositoryTest.java`
- Create: `src/test/java/com/researchassistant/project/ProjectControllerTest.java`
- Modify: `src/main/java/com/researchassistant/ResearchAssistantApplication.java` only if component scan changes are required.

- [ ] **Step 1: Write failing repository tests**

Create `ProjectRepositoryTest` with tests named:

```java
@Test
void createsProjectAndSessionUnderProject()

@Test
void listsSessionsWithoutLeakingOtherProjects()
```

Required assertions:

- created project contains `id`, `topic`, `summary`, `createdAt`, `updatedAt`
- created session contains `projectId`
- listing sessions for project A never returns project B sessions

Run:

```powershell
mvn -Dtest=ProjectRepositoryTest test
```

Expected: fail because repository and schema do not exist.

- [ ] **Step 2: Add migration**

Create tables:

- `research_project`
- `research_session`
- `source_document`
- `assistant_answer`
- `evidence_source`
- `knowledge_candidate`
- `knowledge_entry`
- `stream_event_record`
- `answer_feedback`

Use `uuid` text IDs generated by application code for consistency with existing repository style. Add indexes on `project_id`, `session_id`, `answer_id`, `source_id`, and `run_id`.

- [ ] **Step 3: Implement records and repository**

Implement immutable records for `ProjectRecord` and `ResearchSessionRecord`. `ProjectRepository` must expose:

```java
ProjectRecord createProject(String topic, String summary);
Optional<ProjectRecord> findProject(String projectId);
List<ProjectRecord> listProjects();
ResearchSessionRecord createSession(String projectId, String title);
List<ResearchSessionRecord> listSessions(String projectId);
```

- [ ] **Step 4: Verify repository tests pass**

Run:

```powershell
mvn -Dtest=ProjectRepositoryTest test
```

Expected: tests pass.

- [ ] **Step 5: Write failing controller tests**

Create `ProjectControllerTest` covering:

- `POST /api/projects`
- `GET /api/projects`
- `GET /api/projects/{projectId}`
- `POST /api/projects/{projectId}/sessions`
- `GET /api/projects/{projectId}/sessions`

Run:

```powershell
mvn -Dtest=ProjectControllerTest test
```

Expected: fail before controller exists.

- [ ] **Step 6: Implement controller**

Return JSON fields matching the F002 spec: `id`, `topic`, `summary`, `createdAt`, `updatedAt`, `stats` for project; `id`, `projectId`, `title`, `status`, `lastMessageAt` for session.

- [ ] **Step 7: Verify API slice**

Run:

```powershell
mvn -Dtest=ProjectRepositoryTest,ProjectControllerTest test
```

Expected: both test classes pass.

## Task 2: Redis Stream 事件骨干与 SSE 投影

**Files:**
- Create: `src/main/java/com/researchassistant/events/WorkbenchEvent.java`
- Create: `src/main/java/com/researchassistant/events/WorkbenchEventType.java`
- Create: `src/main/java/com/researchassistant/events/WorkbenchEventPublisher.java`
- Create: `src/main/java/com/researchassistant/events/InMemoryWorkbenchEventPublisher.java`
- Create: `src/main/java/com/researchassistant/events/RedisStreamWorkbenchEventPublisher.java`
- Create: `src/main/java/com/researchassistant/events/WorkbenchEventRepository.java`
- Create: `src/main/java/com/researchassistant/events/SseProjectionController.java`
- Create: `src/test/java/com/researchassistant/events/StreamEventEnvelopeTest.java`
- Create: `src/test/java/com/researchassistant/events/SseProjectionControllerTest.java`
- Modify: `pom.xml` to add Spring Data Redis only when Redis implementation is added in this task.
- Modify: `src/main/resources/application.yml` to add Redis host/port settings.

- [ ] **Step 1: Write failing envelope tests**

Create tests that assert every event has:

- `eventId`
- `eventType`
- `projectId`
- `runId` when event is run-scoped
- `actor`
- monotonically increasing `sequence` per `runId`
- `createdAt`
- non-null `payload`

Run:

```powershell
mvn -Dtest=StreamEventEnvelopeTest test
```

Expected: fail because event classes do not exist.

- [ ] **Step 2: Implement event envelope and type enum**

`WorkbenchEventType` must include all spec event names, including:

```text
run.started
run.completed
run.failed
agent.plan.created
agent.step.started
agent.step.completed
retrieval.started
retrieval.completed
evidence.evaluated
answer.delta
answer.completed
candidate.created
knowledge.entry.created
source.status.changed
memory.flush.started
memory.flush.completed
feedback.applied
```

- [ ] **Step 3: Implement publisher port and in-memory adapter**

`WorkbenchEventPublisher` exposes:

```java
WorkbenchEvent publish(WorkbenchEvent event);
List<WorkbenchEvent> readRunEventsAfter(String runId, String lastEventId);
```

The in-memory adapter is the default test bean and preserves sequence ordering.

- [ ] **Step 4: Write failing SSE projection tests**

`SseProjectionControllerTest` must verify:

- `GET /api/projects/{projectId}/sessions/{sessionId}/runs/{runId}/events` returns SSE format.
- event `id` equals `eventId`.
- event name equals `eventType`.
- `Last-Event-ID` skips already consumed events.

- [ ] **Step 5: Implement SSE projection**

Use Spring `SseEmitter`. The controller reads from `WorkbenchEventPublisher` or `WorkbenchEventRepository`, maps events to SSE, and never exposes Redis details to the frontend.

- [ ] **Step 6: Add Redis adapter**

Add Redis dependency and configuration. `RedisStreamWorkbenchEventPublisher` writes to:

- `project:{projectId}:events`
- `run:{runId}:events`
- `source:{sourceId}:events`

Keep `InMemoryWorkbenchEventPublisher` active under tests.

- [ ] **Step 7: Verify event slice**

Run:

```powershell
mvn -Dtest=StreamEventEnvelopeTest,SseProjectionControllerTest test
```

Expected: tests pass.

## Task 3: 项目级资料接入状态机

**Files:**
- Modify: `src/main/java/com/researchassistant/ingest/model/DocumentStatus.java`
- Modify: `src/main/java/com/researchassistant/ingest/model/FailureStage.java`
- Modify: `src/main/java/com/researchassistant/ingest/DocumentRepository.java`
- Modify: `src/main/java/com/researchassistant/ingest/DocumentController.java`
- Modify: `src/main/java/com/researchassistant/ingest/DocumentIngestService.java`
- Modify: `src/main/java/com/researchassistant/ingest/DocumentProcessingJob.java`
- Create: `src/test/java/com/researchassistant/ingest/ProjectSourceStatusMachineTest.java`

- [ ] **Step 1: Write failing status machine tests**

Cover:

- PDF chain: `uploaded -> parsing -> indexing -> extracting -> indexed -> depositing -> deposited`
- Web chain: `submitted -> fetching -> extracting -> indexed -> depositing -> deposited`
- failure from `parsing` records `failureStage=parsing`
- retry resumes from the failed source and emits another `source.status.changed`

Run:

```powershell
mvn -Dtest=ProjectSourceStatusMachineTest test
```

Expected: fail because project-scoped source status is not implemented.

- [ ] **Step 2: Extend status and failure enums**

Add enum values exactly matching the spec. Preserve any old enum values only if existing tests require them; map old values to new states at API boundary.

- [ ] **Step 3: Scope document import by project**

New endpoint behavior:

```http
POST /api/projects/{projectId}/sources
GET /api/projects/{projectId}/sources
GET /api/projects/{projectId}/sources/{sourceId}
POST /api/projects/{projectId}/sources/{sourceId}/retry
```

The old `/api/documents` endpoints may remain as compatibility wrappers only after the project-scoped path works.

- [ ] **Step 4: Publish source events**

On every status transition, publish `source.status.changed` with `projectId`, `sourceId`, `status`, `failureStage`, and `errorMessage`.

- [ ] **Step 5: Verify ingest slice**

Run:

```powershell
mvn -Dtest=ProjectSourceStatusMachineTest,DocumentControllerTest,DocumentControllerStatusTest,DocumentProcessingJobTest,DocumentProcessingJobFailureTest test
```

Expected: all listed tests pass.

## Task 4: Agent 编排与过程事件

**Files:**
- Modify: `src/main/java/com/researchassistant/orchestrator/SupervisorService.java`
- Modify: `src/main/java/com/researchassistant/orchestrator/TaskRouter.java`
- Modify: `src/main/java/com/researchassistant/orchestrator/PlanExecuteFacade.java`
- Modify: `src/main/java/com/researchassistant/orchestrator/DefaultPlanExecuteFacade.java`
- Modify: `src/main/java/com/researchassistant/chat/ChatController.java`
- Modify: `src/main/java/com/researchassistant/chat/ChatStreamController.java`
- Modify: `src/main/java/com/researchassistant/chat/dto/ChatRequest.java`
- Modify: `src/main/java/com/researchassistant/chat/dto/ChatResponse.java`
- Create: `src/test/java/com/researchassistant/orchestrator/ProjectRunEventFlowTest.java`

- [ ] **Step 1: Write failing run event flow test**

Test one project-scoped question:

```http
POST /api/projects/{projectId}/sessions/{sessionId}/messages
```

Expected event order:

```text
run.started
agent.plan.created
agent.step.started
retrieval.started
retrieval.completed
evidence.evaluated
answer.delta
answer.completed
run.completed
```

Run:

```powershell
mvn -Dtest=ProjectRunEventFlowTest test
```

Expected: fail before project-scoped chat run exists.

- [ ] **Step 2: Add project-scoped chat request**

`ChatRequest` must carry `question`, `sourceFilters`, `allowWebSupplement`, `extractKnowledgeCandidates`, and `answerMode`. The controller path supplies `projectId` and `sessionId`.

- [ ] **Step 3: Emit run lifecycle events**

`SupervisorService` owns `run.started`, planning events, run completion, and run failure. Sub-steps emit actor-specific events through `WorkbenchEventPublisher`.

- [ ] **Step 4: Stream answer delta**

`ChatStreamController` projects `answer.delta` and `answer.completed` through the same SSE envelope used by Task 2. Do not create a second incompatible streaming format.

- [ ] **Step 5: Verify orchestrator slice**

Run:

```powershell
mvn -Dtest=ProjectRunEventFlowTest,SupervisorServiceLogicTest,TaskRouterTest,ChatControllerTest,ChatStreamControllerTest test
```

Expected: all listed tests pass.

## Task 5: 检索、证据边界与联网补充

**Files:**
- Modify: `src/main/java/com/researchassistant/rag/PaperRagService.java`
- Modify: `src/main/java/com/researchassistant/rag/RetrievalTraceView.java`
- Modify: `src/main/java/com/researchassistant/memory/MemoryRecallService.java`
- Modify: `src/main/java/com/researchassistant/evidence/EvidenceBoundaryService.java`
- Modify: `src/main/java/com/researchassistant/evidence/EvidenceLevel.java`
- Modify: `src/main/java/com/researchassistant/evidence/AnswerMode.java`
- Create: `src/test/java/com/researchassistant/evidence/ProjectEvidenceBoundaryTest.java`

- [ ] **Step 1: Write failing evidence boundary tests**

Cover:

- strong paper evidence produces `SUFFICIENT` and `LOCAL_EVIDENCE`
- weak local evidence with web allowed produces `WEAK` and `WEB_SUPPLEMENT`
- no evidence with web disabled produces `NONE` and `REFUSAL`
- L3 memory recall can enrich context but does not count as paper evidence

Run:

```powershell
mvn -Dtest=ProjectEvidenceBoundaryTest test
```

Expected: fail until project-level evidence state is implemented.

- [ ] **Step 2: Separate retrieval modes**

Keep retrieval modes explicit:

```text
NO_RETRIEVAL
MEMORY_RECALL_ONLY
PAPER_RAG_ONLY
MEMORY_THEN_PAPER
WEB_SUPPLEMENT
```

The Supervisor chooses the mode; RAG services do not silently trigger web.

- [ ] **Step 3: Persist evidence sources**

When answering, store `EvidenceSource` records with `sourceType`, `sourceId`, `snippet`, `strength`, `relevanceScore`, `feedbackScore`, and `citationMeta`.

- [ ] **Step 4: Publish evidence event**

After evidence evaluation, publish `evidence.evaluated` with `evidenceState`, `outputMode`, and `citationCount`.

- [ ] **Step 5: Verify retrieval and evidence slice**

Run:

```powershell
mvn -Dtest=ProjectEvidenceBoundaryTest,PaperRagServiceTest,MemoryRecallServiceTest,QueryRewriteServiceTest test
```

Expected: all listed tests pass.

## Task 6: 候选确认与知识板

**Files:**
- Create: `src/main/java/com/researchassistant/candidates/KnowledgeCandidateRecord.java`
- Create: `src/main/java/com/researchassistant/candidates/KnowledgeCandidateRepository.java`
- Create: `src/main/java/com/researchassistant/candidates/KnowledgeCandidateController.java`
- Create: `src/main/java/com/researchassistant/knowledge/KnowledgeEntryRecord.java`
- Create: `src/main/java/com/researchassistant/knowledge/KnowledgeBoardRepository.java`
- Create: `src/main/java/com/researchassistant/knowledge/KnowledgeBoardController.java`
- Create: `src/test/java/com/researchassistant/candidates/KnowledgeCandidateControllerTest.java`
- Create: `src/test/java/com/researchassistant/knowledge/KnowledgeBoardControllerTest.java`

- [ ] **Step 1: Write failing candidate tests**

Cover:

- `GET /api/projects/{projectId}/answers/{answerId}/candidates`
- `POST /api/projects/{projectId}/candidates/{candidateId}/accept`
- `POST /api/projects/{projectId}/candidates/{candidateId}/edit-and-accept`
- `POST /api/projects/{projectId}/candidates/{candidateId}/mark-unverified`
- `POST /api/projects/{projectId}/candidates/{candidateId}/ignore`

Critical assertion: creating a candidate does not create a `KnowledgeEntry`.

- [ ] **Step 2: Implement candidate repository and controller**

Candidate statuses must be exactly:

```text
pending
accepted
edited_accepted
marked_unverified
ignored
```

- [ ] **Step 3: Write failing knowledge board tests**

Cover:

- `GET /api/projects/{projectId}/knowledge-board`
- manual `POST /entries`
- `PATCH /entries/{entryId}`
- `DELETE /entries/{entryId}` archives without hard delete

- [ ] **Step 4: Implement knowledge board**

Sections:

```text
current_candidates
core_concept
method_route
confirmed_finding
open_question
```

Only candidate accept/edit-and-accept or manual create can write `KnowledgeEntry`.

- [ ] **Step 5: Publish knowledge events**

Accepting a candidate publishes `knowledge.entry.created`. Creating a candidate publishes `candidate.created`.

- [ ] **Step 6: Verify candidate and knowledge slice**

Run:

```powershell
mvn -Dtest=KnowledgeCandidateControllerTest,KnowledgeBoardControllerTest test
```

Expected: tests pass.

## Task 7: FeedbackScore 闭环

**Files:**
- Modify: `src/main/java/com/researchassistant/feedback/FeedbackController.java`
- Modify: `src/main/java/com/researchassistant/feedback/FeedbackService.java`
- Modify: `src/main/java/com/researchassistant/rag/KeywordSearchRepository.java`
- Modify: `src/main/java/com/researchassistant/rag/MetadataSearchRepository.java`
- Modify: `src/main/java/com/researchassistant/rag/PgVectorSearchPort.java`
- Create: `src/test/java/com/researchassistant/feedback/ProjectFeedbackServiceTest.java`

- [ ] **Step 1: Write failing feedback tests**

Cover:

- `rating=up` increases feedback score for linked evidence chunks
- `rating=down` decreases feedback score and stores reason
- feedback without `evidenceSourceIds` records answer-level feedback only
- `feedback.applied` event is published

Run:

```powershell
mvn -Dtest=ProjectFeedbackServiceTest test
```

Expected: fail until project feedback API is implemented.

- [ ] **Step 2: Implement project answer feedback API**

Path:

```http
POST /api/projects/{projectId}/answers/{answerId}/feedback
```

Validate `rating` as `up` or `down`.

- [ ] **Step 3: Apply feedback to retrieval scoring**

Adjust retrieval ordering to include `feedbackScore` as a lightweight rerank input. Keep the formula explainable in a small method, for example:

```java
double finalScore(double relevanceScore, double feedbackScore) {
    return relevanceScore + Math.max(-0.2, Math.min(0.2, feedbackScore * 0.05));
}
```

- [ ] **Step 4: Verify feedback slice**

Run:

```powershell
mvn -Dtest=ProjectFeedbackServiceTest,FeedbackControllerTest,PaperRagServiceTest test
```

Expected: all listed tests pass.

## Task 8: 三栏工作台 UI 与前端状态模型

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/css/workbench.css`
- Modify: `src/main/resources/static/js/workbench-model.js`
- Modify: `src/main/resources/static/js/workbench-app.js`
- Modify: `src/main/resources/static/contracts/workbench-api-contract.md`
- Create: `src/main/resources/static/tests/f002-workbench-model.test.mjs`

- [ ] **Step 1: Write failing frontend model tests**

Use Node ESM tests for:

- selecting a session updates `activeSessionId` and clears `activeAnswerContext`
- `source.status.changed` updates the matching source row
- `candidate.created` adds a pending candidate without adding a knowledge entry
- `knowledge.entry.created` adds an entry to the correct section
- duplicate SSE event IDs are ignored

Run:

```powershell
node --test src/main/resources/static/tests/f002-workbench-model.test.mjs
```

Expected: fail before model helpers exist.

- [ ] **Step 2: Implement frontend model helpers**

Expose pure functions from `workbench-model.js`:

```javascript
applySseEvent(state, event)
selectSession(state, sessionId)
selectSidebarView(state, view)
startEditingCandidate(state, candidateId)
applyCandidateAction(state, candidateId, action)
```

- [ ] **Step 3: Update three-column DOM**

`index.html` must contain stable regions:

- `data-region="left-project-sources"`
- `data-region="center-research-dialogue"`
- `data-region="right-research-sidebar"`
- `data-view="knowledge-board"`
- `data-view="evidence-sources"`
- `data-view="candidate-confirmation"`

- [ ] **Step 4: Connect API and SSE**

`workbench-app.js` uses F002 endpoints only for the main flow. Legacy endpoints may remain behind clearly named compatibility functions.

- [ ] **Step 5: Verify frontend model**

Run:

```powershell
node --test src/main/resources/static/tests/f002-workbench-model.test.mjs
```

Expected: tests pass.

- [ ] **Step 6: Browser verification**

Start app:

```powershell
.\scripts\start-dev.cmd
```

Open:

```text
http://localhost:8080
```

Verify:

- desktop shows three usable columns
- narrow viewport does not overlap text or controls
- source status changes appear in the left column
- answer streaming appears in the center
- evidence and candidate actions switch the right sidebar

Capture screenshots or written observations for Evidence.

## Task 9: Full-system Evidence and Harness closeout

**Files:**
- Create: `docs/evidence/EV-010-f002-implementation-validation.md`
- Modify: `docs/features/F002-next-generation-research-workbench.md`
- Modify: `docs/BACKLOG.md`

- [ ] **Step 1: Run backend verification**

Run:

```powershell
mvn test
```

Expected: build succeeds with all tests passing.

- [ ] **Step 2: Run frontend verification**

Run:

```powershell
node --test src/main/resources/static/tests/f002-workbench-model.test.mjs
```

Expected: tests pass.

- [ ] **Step 3: Run Harness verification**

Run:

```powershell
python scripts\knowledge_check.py
```

Expected:

```text
knowledge_check: ok
```

- [ ] **Step 4: Write Evidence**

Create `EV-010-f002-implementation-validation.md` with:

- commands run
- exit status and key output
- API/SSE event samples
- browser verification notes or screenshots
- known limitations
- rollback note

- [ ] **Step 5: Update Feature and Backlog**

Update F002 Feature links to include plan and Evidence. Update status based on actual result:

- `implemented` only after backend, frontend, event, and Harness verification pass.
- `partial` if any F002 acceptance criterion remains unverified.

Update `docs/BACKLOG.md` with the next active step and no broad wish list.

## Commit Strategy

Use one commit per completed slice after its tests pass:

```powershell
git add <slice files>
git commit -m "feat: add project workbench model"
git commit -m "feat: add workbench event stream projection"
git commit -m "feat: add project source status machine"
git commit -m "feat: emit project agent run events"
git commit -m "feat: enforce project evidence boundaries"
git commit -m "feat: add candidate confirmation knowledge board"
git commit -m "feat: apply answer feedback to retrieval scoring"
git commit -m "feat: build f002 workbench UI"
git commit -m "docs: capture f002 validation evidence"
```

Do not commit unrelated dirty files. If a file already contains user changes, inspect it before editing and preserve the user work.

## Self-Review

Spec coverage:

- 产品边界：Task 1-9 cover project model, source import, chat, evidence, candidates, knowledge board, feedback, UI, verification.
- 数据模型：Task 1 creates schema and repositories.
- API 合同：Task 1, 3, 4, 6, 7 implement project-scoped endpoints.
- Redis Stream 事件合同：Task 2 implements event envelope, stream port, SSE projection.
- SSE 前端事件合同：Task 2 and Task 8 implement browser consumption and dedupe.
- Agent 写入边界：Task 4-6 enforce candidate versus confirmed knowledge separation.
- 前端状态模型：Task 8 covers pure state transitions.
- 验收证据：Task 9 creates EV-010 and updates Feature/Backlog. EV-010 is used because EV-002 already belongs to F003.

Placeholder scan:

- 红旗词扫描无命中；每个切片都包含文件边界、验证命令和期望结果。

Type consistency:

- 计划统一使用 `projectId`、`sessionId`、`sourceId`、`answerId`、`runId`、`eventId`、`sequence`。
- 计划统一使用 F002 枚举：`SUFFICIENT/WEAK/NONE`、`LOCAL_EVIDENCE/LOCAL_WEAK_EVIDENCE/WEB_SUPPLEMENT/REFUSAL`、candidate statuses 和 knowledge sections。

## Execution Handoff

Plan complete and saved to `docs/plans/F002-next-generation-research-workbench-plan.md`.

Two execution options:

1. Subagent-Driven (recommended): dispatch a fresh subagent per task, review between tasks, fast iteration.
2. Inline Execution: execute tasks in this session using executing-plans, batch execution with checkpoints.

Recommended choice: Subagent-Driven, because the write sets are now separated enough for parallel Agent work and review checkpoints.
