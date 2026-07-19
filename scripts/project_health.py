from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REQUIRED_PATHS = [
    "README.md",
    "package.json",
    "frontend-ui/package.json",
    "backend-services/pom.xml",
    "ai-gateway/requirements.txt",
    "ai-gateway/README.md",
    "scripts/mock_inventory.py",
    "docs/MOCK_DEPENDENCY_REGISTER.md",
]


def main() -> None:
    problems: list[str] = []
    for rel_path in REQUIRED_PATHS:
        if not (ROOT / rel_path).exists():
            problems.append(f"missing {rel_path}")

    package_file = ROOT / "frontend-ui" / "package.json"
    if package_file.exists():
        package = json.loads(package_file.read_text(encoding="utf-8"))
        for script in ("dev", "build"):
            if script not in package.get("scripts", {}):
                problems.append(f"frontend missing npm script: {script}")

    root_package_file = ROOT / "package.json"
    if root_package_file.exists():
        package = json.loads(root_package_file.read_text(encoding="utf-8"))
        for script in ("health", "ops:check", "audit:mocks", "ci:local"):
            if script not in package.get("scripts", {}):
                problems.append(f"root missing npm script: {script}")

    readme = (ROOT / "README.md").read_text(encoding="utf-8") if (ROOT / "README.md").exists() else ""
    for section in ("Project Modules", "Quick Start", "Practicality Audit", "Quality Notes"):
        if section not in readme:
            problems.append(f"README missing section: {section}")

    if problems:
        for problem in problems:
            print(f"[health] {problem}")
        raise SystemExit(1)

    print("[health] shanxi repository checks passed")


if __name__ == "__main__":
    main()
