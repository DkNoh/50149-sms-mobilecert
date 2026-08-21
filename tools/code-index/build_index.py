#!/usr/bin/env python3
"""Build a compact, content-free Java structure index for coordinator use."""

from __future__ import annotations

import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
OUTPUT = REPO_ROOT / "tools" / "code-index" / "out" / "index.json"
SOURCE_ROOTS = (
    ("main", REPO_ROOT / "src" / "main" / "java"),
    ("test", REPO_ROOT / "src" / "test" / "java"),
)
PACKAGE_RE = re.compile(r"(?m)^\s*package\s+([\w.]+)\s*;")
IMPORT_RE = re.compile(r"(?m)^\s*import\s+(?:static\s+)?([\w.*]+)\s*;")
TYPE_RE = re.compile(
    r"(?m)^\s*(?:public|protected|private|abstract|final|static|strictfp|\s)*"
    r"\b(class|interface|enum|record)\s+([A-Za-z_$][\w$]*)"
)


def read_source(path: Path) -> tuple[str, str]:
    raw = path.read_bytes()
    for encoding in ("utf-8", "euc-kr", "cp949"):
        try:
            return raw.decode(encoding), encoding
        except UnicodeDecodeError:
            continue
    return raw.decode("iso-8859-1"), "iso-8859-1"


def line_number(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def index_file(scope: str, path: Path) -> dict[str, object]:
    text, encoding = read_source(path)
    package_match = PACKAGE_RE.search(text)
    types = [
        {"kind": match.group(1), "name": match.group(2), "line": line_number(text, match.start())}
        for match in TYPE_RE.finditer(text)
    ]
    return {
        "path": path.relative_to(REPO_ROOT).as_posix(),
        "scope": scope,
        "encoding": encoding,
        "package": package_match.group(1) if package_match else "",
        "types": types,
        "imports": sorted(set(IMPORT_RE.findall(text))),
        "lines": len(text.splitlines()),
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
    }


def main() -> None:
    files: list[dict[str, object]] = []
    for scope, source_root in SOURCE_ROOTS:
        if not source_root.is_dir():
            continue
        for path in sorted(source_root.rglob("*.java")):
            files.append(index_file(scope, path))

    package_count = len({item["package"] for item in files if item["package"]})
    type_count = sum(len(item["types"]) for item in files)
    payload = {
        "schema_version": "1.0",
        "repository": REPO_ROOT.name,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "summary": {
            "java_files": len(files),
            "packages": package_count,
            "types": type_count,
        },
        "files": files,
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Indexed {len(files)} Java files, {type_count} types, {package_count} packages")
    print(OUTPUT.relative_to(REPO_ROOT))


if __name__ == "__main__":
    main()
