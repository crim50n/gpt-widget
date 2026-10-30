#!/usr/bin/env python3
"""Check Git-visible files without printing potential secret contents."""
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN_SUFFIXES = {".keystore", ".jks", ".p12", ".pem", ".key", ".apk", ".aab", ".hprof"}
TEXT_SUFFIXES = {".java", ".xml", ".md", ".gradle", ".properties", ".yml", ".yaml", ".py", ".json"}
PRIVATE_KEY = re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----")
API_KEY = re.compile(r"\bsk-(?:proj-)?[A-Za-z0-9_-]{40,}\b")


def main():
    output = subprocess.check_output(
        ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"], cwd=ROOT
    )
    paths = sorted(set(value.decode("utf-8") for value in output.split(b"\0") if value))
    errors = []
    for name in paths:
        path = ROOT / name
        if not path.is_file():
            continue
        if (path.suffix.lower() in FORBIDDEN_SUFFIXES or path.name in {"signing.properties", "auth.json"}
                or name.startswith((".local/", "screenshots/", ".gradle/", "release-apks/"))
                or "/build/" in name or name.startswith("build/")):
            errors.append(f"{name}: private/generated file is visible to Git")
            continue
        if path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        text = path.read_text(encoding="utf-8")
        if PRIVATE_KEY.search(text) or API_KEY.search(text):
            errors.append(f"{name}: possible private key or API credential")
        if re.search(r"/home/(?:live)/|/tmp/(?:opencode)/", text):
            errors.append(f"{name}: machine-specific workspace path")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Publication check OK: {len(paths)} Git-visible files; no signing material or generated outputs.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
