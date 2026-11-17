#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "=== Checking Modular Architecture Boundaries ==="

FAILED=0

# 1. Check feature-to-feature dependencies: Features MUST NEVER depend on other features
echo "1. Checking feature isolation (no feature-to-feature dependencies)..."
for build_file in feature/*/build.gradle.kts; do
    if [ ! -f "$build_file" ]; then
        continue
    fi
    MODULE_NAME=$(dirname "$build_file")
    
    # Check for direct feature project references
    if grep -E 'project\(":feature:|projects\.feature\.' "$build_file" > /dev/null; then
        echo "  [ERROR] $build_file violates feature isolation by depending on another feature!"
        grep -E 'project\(":feature:|projects\.feature\.' "$build_file"
        FAILED=1
    else
        echo "  [OK] $MODULE_NAME has no sibling feature dependencies."
    fi
done

# 2. Check core layer dependencies
echo "2. Checking core layer boundaries..."
if grep -E 'project\(":core:|projects\.core\.' core/model/build.gradle.kts 2>/dev/null | grep -v 'core:model' > /dev/null; then
    echo "  [ERROR] :core:model must have no dependencies on other core modules!"
    FAILED=1
else
    echo "  [OK] :core:model is pure domain model."
fi

if grep -E 'project\(":core:database"|project\(":core:network"|projects\.core\.database|projects\.core\.network' core/ui/build.gradle.kts 2>/dev/null > /dev/null; then
    echo "  [ERROR] :core:ui must not depend on database or network!"
    FAILED=1
else
    echo "  [OK] :core:ui depends only on design tokens and models."
fi

# School journal is removable (ADR 0017): no core module may know about it.
if grep -rniE 'school-?journal' core --include='*.kt' --include='*.kts' --include='*.xml' \
    --exclude-dir=build 2>/dev/null; then
    echo "  [ERROR] core/ must not mention school-journal (ADR 0017: the module stays removable)!"
    FAILED=1
else
    echo "  [OK] core/ does not mention school-journal."
fi

# 3. Check AGENTS.md existence and line count (< 80 lines for modular docs)
echo "3. Checking module AGENTS.md documentation..."
MODULES=(
    "core/model"
    "core/ui"
    "core/database"
    "core/network"
    "feature/shopping"
    "feature/events"
    "feature/school"
    "feature/family"
    "feature/wall"
    "feature/frame"
    "feature/timeline"
    "app"
)
# Optional modules (ADR 0017): checked only while they exist, so removing one keeps this green.
for optional in "feature/school-journal"; do
    if [ -d "$optional" ]; then
        MODULES+=("$optional")
    fi
done

for mod in "${MODULES[@]}"; do
    AGENTS_FILE="$mod/AGENTS.md"
    if [ ! -f "$AGENTS_FILE" ]; then
        echo "  [ERROR] Missing $AGENTS_FILE"
        FAILED=1
    else
        LINES=$(wc -l < "$AGENTS_FILE" | tr -d ' ')
        if [ "$LINES" -gt 80 ]; then
            echo "  [WARN] $AGENTS_FILE is $LINES lines (recommended < 80 lines for agent context)"
        else
            echo "  [OK] $AGENTS_FILE exists ($LINES lines)."
        fi
    fi
done

if [ "$FAILED" -ne 0 ]; then
    echo "=== Modular boundaries check FAILED ==="
    exit 1
fi

echo "=== All modular boundaries checks PASSED ==="
