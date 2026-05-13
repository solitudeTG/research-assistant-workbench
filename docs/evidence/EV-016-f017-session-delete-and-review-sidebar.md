---
id: EV-016
doc_kind: evidence
status: completed
date: 2026-05-13
feature_ids: [F017]
---
# F017 Session Delete and Review Sidebar Evidence

## 证据

- 命令：`& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectControllerTest' test`
- 结果：通过，`Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`。
- 覆盖范围：项目会话创建/列表/重命名基线，`DELETE /api/projects/{projectId}/sessions/{sessionId}` 成功返回 `204`，跨项目删除返回 `404`，删除后会话列表移除目标会话，关联的 `assistant_answer`、`evidence_source`、`knowledge_candidate`、`stream_event_record`、legacy `chat_session`、`chat_message`、`retrieval_trace` 和 `memory_entry` 被清理。

- 命令：`node --test src/main/resources/static/tests/workbench-model.test.mjs`
- 结果：通过，`tests 5`，`pass 5`。
- 覆盖范围：session delete URL 构造、删除当前会话后的前端状态切换、消息/证据/候选/trace 清空。

- 命令：`node --test src/main/resources/static/tests/f002-workbench-model.test.mjs`
- 结果：通过，`tests 18`，`pass 18`。
- 覆盖范围：原 F002/F010 前端模型回归，包括 workspace 切换、SSE 投影、候选/知识事件和研究过程 summary。

- 命令：`node --check src/main/resources/static/js/workbench-app.js`
- 结果：通过，无语法错误输出。
- 覆盖范围：静态前端入口脚本语法检查。

- 命令：`python scripts/knowledge_check.py .`
- 结果：`knowledge_check: ok`。
- 覆盖范围：Harness Markdown 文档结构检查。

- 命令：`.\scripts\rebuild-dev.cmd`
- 结果：Docker app image 重新构建成功，容器重建并启动。
- 覆盖范围：改动后的 Java 与静态资源进入运行镜像。

- 命令：`Invoke-RestMethod -Uri 'http://localhost:8080/actuator/health'`
- 结果：`{"status":"UP"}`。
- 覆盖范围：重建后的应用健康检查。

- 命令：使用 bundled Node + 系统 Chrome + Playwright 对 `http://localhost:8080` 做无头浏览器断言。
- 结果：通过，输出：

```json
{
  "deleteButtons": 8,
  "renameButtons": 8,
  "hasReviewCopy": true,
  "hasFinalEvidence": true,
  "dialogSeen": true,
  "beforeCount": 8,
  "afterCancelCount": 8,
  "screenshotPath": "E:\\Self-Project\\research-assistant-workbench\\target\\f017-workbench-ui.png"
}
```

- 覆盖范围：会话行显示重命名和删除操作；删除弹出包含“无法撤销”的二次确认；取消后会话数量不变；右侧显示“当前回答审阅”和“最终证据来源”。

## 备注

- Codex in-app Browser 插件初始化连续超时，因此浏览器验证改用系统 Chrome 的无头 Playwright 检查；这不改变验证目标。
- 仓库当前存在 F016 检索观测相关未提交改动；本 Evidence 只覆盖 F017 会话删除和右侧审阅区语义收束。
