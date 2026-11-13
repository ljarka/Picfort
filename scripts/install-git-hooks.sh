#!/bin/sh
# Uses the versioned hooks in scripts/git-hooks/ (pre-commit: ktlintFormat, ADR 0013).
# Run once per clone. Undo: git config --unset core.hooksPath
cd "$(git rev-parse --show-toplevel)" || exit 1
chmod +x scripts/git-hooks/*
git config core.hooksPath scripts/git-hooks
echo "Git hooks installed: $(ls scripts/git-hooks | tr '\n' ' ')"
