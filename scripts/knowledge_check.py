from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path


@dataclass
class CheckResult:
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    @property
    def ok(self) -> bool:
        return not self.errors


def check_workspace(root: Path | str) -> CheckResult:
    root = Path(root)
    docs = root / "docs"
    result = CheckResult()

    _require_file(result, docs / "BACKLOG.md")
    for directory in ("features", "decisions", "evidence"):
        _require_dir(result, docs / directory)

    if (docs / "features").exists():
        for path in sorted((docs / "features").glob("F*.md")):
            _check_markdown_artifact(
                result,
                path,
                expected_id_prefix="F",
                required_headings=(("Goal", "目标"), ("Acceptance Criteria", "验收标准"), ("Links", "链接")),
            )

    if (docs / "decisions").exists():
        for path in sorted((docs / "decisions").glob("ADR-*.md")):
            _check_markdown_artifact(
                result,
                path,
                expected_id_prefix="ADR-",
                required_headings=(("Decision", "决策"), ("Alternatives Considered", "备选方案"), ("Consequences", "影响")),
            )

    if (docs / "evidence").exists():
        for path in sorted((docs / "evidence").glob("EV-*.md")):
            _check_markdown_artifact(
                result,
                path,
                expected_id_prefix="EV-",
                required_headings=(("Evidence", "证据"),),
            )

    return result


def _require_file(result: CheckResult, path: Path) -> None:
    if not path.exists() or not path.is_file():
        result.errors.append(f"Missing required file: {path}")


def _require_dir(result: CheckResult, path: Path) -> None:
    if not path.exists() or not path.is_dir():
        result.errors.append(f"Missing required directory: {path}")


def _check_markdown_artifact(
    result: CheckResult,
    path: Path,
    expected_id_prefix: str,
    required_headings: tuple[tuple[str, ...], ...],
) -> None:
    text = path.read_text(encoding="utf-8")
    frontmatter = _frontmatter(text)
    artifact_id = frontmatter.get("id")
    status = frontmatter.get("status")

    if not artifact_id:
        result.errors.append(f"{path}: missing frontmatter id")
    elif not artifact_id.startswith(expected_id_prefix):
        result.errors.append(f"{path}: id {artifact_id!r} must start with {expected_id_prefix!r}")

    if not status:
        result.errors.append(f"{path}: missing frontmatter status")

    if not re.search(r"^#\s+\S", text, flags=re.MULTILINE):
        result.errors.append(f"{path}: missing top-level title")

    for heading_options in required_headings:
        if not any(re.search(rf"^##\s+{re.escape(heading)}\s*$", text, flags=re.MULTILINE) for heading in heading_options):
            result.errors.append(f"{path}: missing required heading '## {heading_options[0]}'")


def _frontmatter(text: str) -> dict[str, str]:
    if not text.startswith("---\n"):
        return {}
    end = text.find("\n---", 4)
    if end == -1:
        return {}

    fields: dict[str, str] = {}
    for line in text[4:end].splitlines():
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        fields[key.strip()] = value.strip().strip('"')
    return fields


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Validate lightweight Harness markdown artifacts.")
    parser.add_argument("root", nargs="?", default=".", help="Repository root to validate.")
    args = parser.parse_args(argv)

    result = check_workspace(Path(args.root).resolve())
    for warning in result.warnings:
        print(f"WARN: {warning}")
    for error in result.errors:
        print(f"ERROR: {error}")

    if result.ok:
        print("knowledge_check: ok")
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
