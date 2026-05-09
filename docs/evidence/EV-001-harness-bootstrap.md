---
id: EV-001
status: captured
date: 2026-05-09
feature_ids: [F001]
---
# Harness 启动证据

## 证据

- 命令：`python -m unittest scripts/test_knowledge_check.py`
- 结果：

```text
...
----------------------------------------------------------------------
Ran 3 tests in 0.040s

OK
```

- 命令：`python scripts/knowledge_check.py`
- 结果：

```text
knowledge_check: ok
```

## 备注

第一次测试运行失败是因为 `scripts/knowledge_check.py` 尚不存在，确认了红灯步骤。随后新增中文标题测试，确认校验器支持中文 Harness 文档，不再强迫 Feature、ADR、Evidence 使用英文小节名。
