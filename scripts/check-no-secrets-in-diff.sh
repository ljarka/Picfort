#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BASE_REF="${DIFF_BASE:-${1:-}}"
HEAD_REF="${DIFF_HEAD:-${2:-}}"

if [[ -z "$BASE_REF" || -z "$HEAD_REF" ]]; then
  if [[ -n "${GITHUB_BASE_REF:-}" && -n "${GITHUB_SHA:-}" ]]; then
    BASE_REF="origin/${GITHUB_BASE_REF}"
    HEAD_REF="${GITHUB_SHA}"
  else
    BASE_REF="HEAD~1"
    HEAD_REF="HEAD"
  fi
fi

if ! git rev-parse --verify "${BASE_REF}^{commit}" >/dev/null 2>&1; then
  echo "[WARN] Cannot resolve base ref '${BASE_REF}', skipping secret diff scan."
  exit 0
fi
if ! git rev-parse --verify "${HEAD_REF}^{commit}" >/dev/null 2>&1; then
  echo "[WARN] Cannot resolve head ref '${HEAD_REF}', skipping secret diff scan."
  exit 0
fi

DIFF_RANGE="${BASE_REF}...${HEAD_REF}"

echo "=== Secret scan (added lines only) ==="
echo "Diff range: $DIFF_RANGE"

TMP_DIFF_FILE="$(mktemp)"
trap 'rm -f "$TMP_DIFF_FILE"' EXIT
git diff --unified=0 --no-color "$DIFF_RANGE" > "$TMP_DIFF_FILE"

python3 - "$TMP_DIFF_FILE" <<'PY'
import re
import sys

patterns = [
    ("AWS access key", re.compile(r"AKIA[0-9A-Z]{16}")),
    ("GitHub token", re.compile(r"gh[pousr]_[A-Za-z0-9_]{36,255}")),
    ("Google API key", re.compile(r"AIza[0-9A-Za-z\-_]{35}")),
    ("Slack token", re.compile(r"xox[baprs]-[A-Za-z0-9-]{10,}")),
    ("Private key header", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |PGP |DSA )?PRIVATE KEY-----")),
]

current_file = "<unknown>"
new_line = 0
findings = []
hunk_re = re.compile(r"@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@")

with open(sys.argv[1], "r", encoding="utf-8", errors="replace") as diff:
    for raw in diff:
        line = raw.rstrip("\n")
        if line.startswith("+++ b/"):
            current_file = line[6:]
            continue
        if line.startswith("@@"):
            m = hunk_re.match(line)
            if m:
                new_line = int(m.group(1)) - 1
            continue
        if line.startswith("+") and not line.startswith("+++"):
            new_line += 1
            added = line[1:]
            for name, pattern in patterns:
                if pattern.search(added):
                    findings.append((current_file, new_line, name, added.strip()))
                    break
            continue
        if line.startswith(" "):
            new_line += 1

if findings:
    print("[ERROR] Potential secrets found in added lines:")
    for file, line_no, kind, snippet in findings:
        print(f"  - {file}:{line_no} [{kind}] {snippet}")
    sys.exit(1)

print("[OK] No high-confidence secrets found in added lines.")
PY
