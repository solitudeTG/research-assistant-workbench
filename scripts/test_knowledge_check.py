import importlib.util
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path


def load_knowledge_check():
    script_path = Path(__file__).with_name("knowledge_check.py")
    spec = importlib.util.spec_from_file_location("knowledge_check", script_path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class KnowledgeCheckTest(unittest.TestCase):
    def test_accepts_minimal_valid_harness_workspace(self):
        module = load_knowledge_check()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "docs" / "features").mkdir(parents=True)
            (root / "docs" / "decisions").mkdir(parents=True)
            (root / "docs" / "evidence").mkdir(parents=True)
            (root / "docs" / "BACKLOG.md").write_text("# Backlog\n\n- Active work is tracked here.\n", encoding="utf-8")
            (root / "docs" / "features" / "F001-test.md").write_text(textwrap.dedent("""\
                ---
                id: F001
                status: active
                ---
                # Test Feature

                ## Goal
                Keep a valid feature page.

                ## Acceptance Criteria
                - It has a checkable outcome.

                ## Links
                - Evidence: ../evidence/EV-001-test.md
                """), encoding="utf-8")
            (root / "docs" / "decisions" / "ADR-001-test.md").write_text(textwrap.dedent("""\
                ---
                id: ADR-001
                status: accepted
                ---
                # Test ADR

                ## Decision
                Use markdown as source of truth.

                ## Alternatives Considered
                - Database-first registry.

                ## Consequences
                - Easy review in git.
                """), encoding="utf-8")
            (root / "docs" / "evidence" / "EV-001-test.md").write_text(textwrap.dedent("""\
                ---
                id: EV-001
                status: captured
                ---
                # Test Evidence

                ## Evidence
                Command: `python -m unittest`
                Result: pass.
                """), encoding="utf-8")

            result = module.check_workspace(root)

        self.assertEqual([], result.errors)

    def test_rejects_feature_without_acceptance_criteria(self):
        module = load_knowledge_check()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "docs" / "features").mkdir(parents=True)
            (root / "docs" / "decisions").mkdir(parents=True)
            (root / "docs" / "evidence").mkdir(parents=True)
            (root / "docs" / "BACKLOG.md").write_text("# Backlog\n", encoding="utf-8")
            (root / "docs" / "features" / "F001-broken.md").write_text(textwrap.dedent("""\
                ---
                id: F001
                status: active
                ---
                # Broken Feature

                ## Goal
                This page is missing acceptance criteria.
                """), encoding="utf-8")

            result = module.check_workspace(root)

        self.assertTrue(any("Acceptance Criteria" in error for error in result.errors))

    def test_accepts_chinese_harness_headings(self):
        module = load_knowledge_check()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "docs" / "features").mkdir(parents=True)
            (root / "docs" / "decisions").mkdir(parents=True)
            (root / "docs" / "evidence").mkdir(parents=True)
            (root / "docs" / "BACKLOG.md").write_text("# 工作看板\n", encoding="utf-8")
            (root / "docs" / "features" / "F001-chinese.md").write_text(textwrap.dedent("""\
                ---
                id: F001
                status: active
                ---
                # 中文 Feature

                ## 目标
                允许中文 Harness 文档。

                ## 验收标准
                - 校验器接受中文标题。

                ## 链接
                - 证据：../evidence/EV-001-chinese.md
                """), encoding="utf-8")
            (root / "docs" / "decisions" / "ADR-001-chinese.md").write_text(textwrap.dedent("""\
                ---
                id: ADR-001
                status: accepted
                ---
                # 中文 ADR

                ## 决策
                中文文档是事实源。

                ## 备选方案
                - 英文标题。

                ## 影响
                - 更适合项目协作。
                """), encoding="utf-8")
            (root / "docs" / "evidence" / "EV-001-chinese.md").write_text(textwrap.dedent("""\
                ---
                id: EV-001
                status: captured
                ---
                # 中文证据

                ## 证据
                命令通过。
                """), encoding="utf-8")

            result = module.check_workspace(root)

        self.assertEqual([], result.errors)


if __name__ == "__main__":
    unittest.main()
