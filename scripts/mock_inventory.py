from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "frontend-ui" / "src"
DEFAULT_DOC = ROOT / "docs" / "MOCK_DEPENDENCY_REGISTER.md"
SKIP_PARTS = {"node_modules", "dist", ".git"}
PATTERN = re.compile(r"mock|fallback|回退|兜底|演示", re.IGNORECASE)
REAL_INTEGRATION_PATTERNS = (
    "/api/",
    "fetch(",
    "request<",
    "WebSocket",
    "API_BASE",
)


@dataclass(frozen=True)
class Finding:
    path: Path
    line_number: int
    line: str

    @property
    def relative_path(self) -> str:
        return self.path.relative_to(ROOT).as_posix()

    @property
    def category(self) -> str:
        lower = self.line.lower()
        if "fallback" in lower or "回退" in self.line or "兜底" in self.line:
            return "runtime fallback"
        if "mock" in lower:
            return "mock data"
        return "demo copy"


def iter_source_files() -> list[Path]:
    if not SOURCE_ROOT.exists():
        return []
    files: list[Path] = []
    for path in SOURCE_ROOT.rglob("*"):
        if not path.is_file():
            continue
        if any(part in SKIP_PARTS for part in path.parts):
            continue
        if path.suffix.lower() not in {".ts", ".tsx", ".vue", ".js", ".jsx"}:
            continue
        files.append(path)
    return sorted(files)


def collect_findings() -> list[Finding]:
    findings: list[Finding] = []
    for path in iter_source_files():
        try:
            lines = path.read_text(encoding="utf-8").splitlines()
        except UnicodeDecodeError:
            lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
        for index, line in enumerate(lines, start=1):
            if PATTERN.search(line):
                findings.append(Finding(path=path, line_number=index, line=line.strip()))
    return findings


def count_real_integrations() -> int:
    count = 0
    for path in iter_source_files():
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            text = path.read_text(encoding="utf-8", errors="replace")
        if any(pattern in text for pattern in REAL_INTEGRATION_PATTERNS):
            count += 1
    return count


def render_markdown(findings: list[Finding]) -> str:
    by_category: dict[str, list[Finding]] = {}
    for finding in findings:
        by_category.setdefault(finding.category, []).append(finding)

    lines = [
        "# Mock Dependency Register",
        "",
        "This register tracks frontend mock data, offline fallbacks, and demo-only copy so the project stays honest about what is production-integrated versus illustrative.",
        "",
        "## Current Snapshot",
        "",
        f"- Frontend source files scanned: {len(iter_source_files())}",
        f"- Mock/fallback/demo references found: {len(findings)}",
        f"- Source files with API or gateway integration code: {count_real_integrations()}",
        "",
        "## Operating Policy",
        "",
        "- Runtime fallbacks may keep the learning UI usable when backend services are offline, but each fallback must show a user-facing degraded-state message.",
        "- Mock data should stay deterministic and local; do not mix it with persisted learner records.",
        "- New mock assets should include the backend endpoint or product decision needed to retire them.",
        "- Run `npm run audit:mocks` from the repository root before publishing changes that add learning-workbench features.",
        "",
        "## Inventory",
        "",
    ]

    for category in ("runtime fallback", "mock data", "demo copy"):
        items = by_category.get(category, [])
        lines.append(f"### {category.title()}")
        lines.append("")
        if not items:
            lines.append("- None found.")
            lines.append("")
            continue
        for item in items[:80]:
            preview = item.line.replace("|", "\\|")
            lines.append(f"- `{item.relative_path}:{item.line_number}` - {preview}")
        if len(items) > 80:
            lines.append(f"- ... {len(items) - 80} additional references omitted; run the audit script for the full list.")
        lines.append("")

    lines.extend(
        [
            "## Retirement Backlog",
            "",
            "| Area | Current fallback | Practical next step |",
            "| --- | --- | --- |",
            "| Marketplace vault | Local `MOCK_VAULT` records when `/api/v1/rag/public-vault` is empty or unavailable | Seed backend public-vault data and show an empty state only when the API returns no records |",
            "| RAG visual workbench | Generated document chunks and search hits after upload/search failures | Add a local fixture upload endpoint for development and reserve mock chunks for explicit demo mode |",
            "| Knowledge map | Static graph data and per-node materials | Back graph nodes with `backend-services/sql` seed data and expose a read-only graph endpoint |",
            "| Auth portal | Simulated login/register/reset flows | Wire to backend auth endpoints or label the module as a prototype until auth is ready |",
            "| Tutorial videos | Placeholder modal copy | Replace with hosted videos or remove the modal from production navigation |",
            "",
        ],
    )
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description="Inventory frontend mock and fallback dependencies.")
    parser.add_argument("--write", action="store_true", help="Rewrite docs/MOCK_DEPENDENCY_REGISTER.md")
    args = parser.parse_args()

    findings = collect_findings()
    markdown = render_markdown(findings)
    if args.write:
        DEFAULT_DOC.write_text(markdown, encoding="utf-8")

    print(f"[mock-inventory] scanned={len(iter_source_files())} findings={len(findings)} integrations={count_real_integrations()}")
    print("[mock-inventory] categories:")
    for category in ("runtime fallback", "mock data", "demo copy"):
        print(f"  - {category}: {sum(1 for finding in findings if finding.category == category)}")
    if not DEFAULT_DOC.exists():
        raise SystemExit("[mock-inventory] missing docs/MOCK_DEPENDENCY_REGISTER.md; run with --write")


if __name__ == "__main__":
    main()
