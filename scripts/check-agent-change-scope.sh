#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

MAX_FILES_CHANGED="${MAX_FILES_CHANGED:-60}"
MAX_MODULES_TOUCHED="${MAX_MODULES_TOUCHED:-6}"
CRITICAL_MAX_MODULES="${CRITICAL_MAX_MODULES:-3}"
ALLOW_LARGE_AGENT_DIFF="${ALLOW_LARGE_AGENT_DIFF:-0}"

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
  echo "[WARN] Cannot resolve base ref '${BASE_REF}', skipping scope guard."
  exit 0
fi
if ! git rev-parse --verify "${HEAD_REF}^{commit}" >/dev/null 2>&1; then
  echo "[WARN] Cannot resolve head ref '${HEAD_REF}', skipping scope guard."
  exit 0
fi

DIFF_RANGE="${BASE_REF}...${HEAD_REF}"
CHANGED_FILES=()
while IFS= read -r file; do
  [[ -n "$file" ]] && CHANGED_FILES+=("$file")
done < <(git diff --name-only --diff-filter=ACMR "$DIFF_RANGE")

if [[ ${#CHANGED_FILES[@]} -eq 0 ]]; then
  echo "[OK] No changed files in ${DIFF_RANGE}."
  exit 0
fi

extract_module() {
  local path="$1"
  case "$path" in
    # README.md, AGENTS.md and other notes are documentation, not code modules.
    *.md) echo "docs" ;;
    feature/*/*) echo "$(cut -d/ -f1-2 <<< "$path")" ;;
    core/*/*) echo "$(cut -d/ -f1-2 <<< "$path")" ;;
    app/*) echo "app" ;;
    picfort-server/*) echo "picfort-server" ;;
    .github/*) echo ".github" ;;
    scripts/*) echo "scripts" ;;
    docs/*) echo "docs" ;;
    *) echo "${path%%/*}" ;;
  esac
}

ALL_MODULES_RAW=()
CODE_MODULES_RAW=()

CRITICAL_PATHS=(
  "app/src/main/java/com/github/ljarka/picfort/MainActivity.kt"
  "app/src/main/java/com/github/ljarka/picfort/AppNavigation.kt"
  "core/network/src/main/java/com/github/ljarka/picfort/core/network/"
  "picfort-server/app.py"
  ".github/workflows/"
)

critical_touched=0

for file in "${CHANGED_FILES[@]}"; do
  module="$(extract_module "$file")"
  ALL_MODULES_RAW+=("$module")
  if [[ "$module" != "docs" ]]; then
    CODE_MODULES_RAW+=("$module")
  fi

  for critical in "${CRITICAL_PATHS[@]}"; do
    if [[ "$file" == "$critical" || "$file" == "$critical"* ]]; then
      critical_touched=1
      break
    fi
  done
done

if [[ ${#ALL_MODULES_RAW[@]} -gt 0 ]]; then
  IFS=$'\n' all_modules_sorted=($(printf "%s\n" "${ALL_MODULES_RAW[@]}" | sort -u))
  unset IFS
else
  all_modules_sorted=()
fi
if [[ ${#CODE_MODULES_RAW[@]} -gt 0 ]]; then
  IFS=$'\n' code_modules_sorted=($(printf "%s\n" "${CODE_MODULES_RAW[@]}" | sort -u))
  unset IFS
else
  code_modules_sorted=()
fi

file_count=${#CHANGED_FILES[@]}
module_count=${#all_modules_sorted[@]}
code_module_count=${#code_modules_sorted[@]}

echo "=== Agent change-scope guard ==="
echo "Diff range: $DIFF_RANGE"
echo "Changed files: $file_count"
echo "Touched modules (all): ${all_modules_sorted[*]}"
echo "Touched modules (excluding docs): ${code_modules_sorted[*]}"
if [[ "$critical_touched" -eq 1 ]]; then
  echo "Critical paths touched: yes"
else
  echo "Critical paths touched: no"
fi

declare -a violations=()

if [[ "$file_count" -gt "$MAX_FILES_CHANGED" ]]; then
  violations+=("changed files ${file_count} > ${MAX_FILES_CHANGED}")
fi
if [[ "$code_module_count" -gt "$MAX_MODULES_TOUCHED" ]]; then
  violations+=("code modules ${code_module_count} > ${MAX_MODULES_TOUCHED}")
fi
if [[ "$critical_touched" -eq 1 && "$code_module_count" -gt "$CRITICAL_MAX_MODULES" ]]; then
  violations+=("critical paths changed with wide scope (${code_module_count} code modules > ${CRITICAL_MAX_MODULES})")
fi

if [[ ${#violations[@]} -eq 0 ]]; then
  echo "[OK] Scope is within guardrail thresholds."
  exit 0
fi

if [[ "$ALLOW_LARGE_AGENT_DIFF" == "1" ]]; then
  echo "[WARN] Scope violations ignored (ALLOW_LARGE_AGENT_DIFF=1):"
  for v in "${violations[@]}"; do
    echo "  - $v"
  done
  exit 0
fi

echo "[ERROR] Scope guardrail failed:"
for v in "${violations[@]}"; do
  echo "  - $v"
done
echo "Split the change into smaller PRs, or set ALLOW_LARGE_AGENT_DIFF=1 with explicit reviewer approval."
exit 1
