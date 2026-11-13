#!/usr/bin/env python3
from __future__ import annotations

import argparse
import glob
import json
import os
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path


def run_git_changed_files(base_ref: str | None, head_ref: str | None) -> list[str]:
    if base_ref and head_ref:
        diff_range = f"{base_ref}...{head_ref}"
    else:
        diff_range = "HEAD~1...HEAD"
    try:
        result = subprocess.run(
            ["git", "diff", "--name-only", "--diff-filter=ACMR", diff_range],
            check=True,
            capture_output=True,
            text=True,
        )
    except subprocess.CalledProcessError:
        return []
    return [line.strip() for line in result.stdout.splitlines() if line.strip()]


def module_of(path: str) -> str:
    parts = path.split("/")
    if len(parts) >= 2 and parts[0] in {"feature", "core"}:
        return f"{parts[0]}/{parts[1]}"
    if parts[0] in {"app", "picfort-server", "docs", "scripts", ".github"}:
        return parts[0]
    return parts[0]


def collect_junit() -> dict:
    reports = glob.glob("**/build/test-results/**/TEST-*.xml", recursive=True)
    totals = Counter(tests=0, failures=0, errors=0, skipped=0)

    for report in reports:
        try:
            root = ET.parse(report).getroot()
        except ET.ParseError:
            continue
        totals["tests"] += int(root.attrib.get("tests", 0))
        totals["failures"] += int(root.attrib.get("failures", 0))
        totals["errors"] += int(root.attrib.get("errors", 0))
        totals["skipped"] += int(root.attrib.get("skipped", 0))

    return {
        "reports_count": len(reports),
        "tests_total": totals["tests"],
        "failures_total": totals["failures"],
        "errors_total": totals["errors"],
        "skipped_total": totals["skipped"],
    }


def collect_paparazzi() -> dict:
    all_failures = glob.glob("**/build/paparazzi/failures/**/*.png", recursive=True)
    deltas = glob.glob("**/build/paparazzi/failures/**/delta-*.png", recursive=True)
    return {
        "failure_png_count": len(all_failures),
        "delta_count": len(deltas),
    }


def build_payload(args: argparse.Namespace) -> dict:
    changed_files = run_git_changed_files(args.diff_base, args.diff_head)
    modules = sorted({module_of(p) for p in changed_files})
    code_modules = sorted(m for m in modules if m != "docs")

    android_checks_status = os.getenv("ANDROID_CHECKS_STATUS", "unknown")
    scope_guard_status = os.getenv("SCOPE_GUARD_STATUS", "unknown")
    secret_scan_status = os.getenv("SECRET_SCAN_STATUS", "unknown")

    all_green = all(
        status in {"success", "skipped"}
        for status in (android_checks_status, scope_guard_status, secret_scan_status)
    )

    return {
        "schema_version": 1,
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "git": {
            "sha": os.getenv("GITHUB_SHA"),
            "ref": os.getenv("GITHUB_REF"),
            "event_name": os.getenv("GITHUB_EVENT_NAME"),
            "run_id": os.getenv("GITHUB_RUN_ID"),
        },
        "checks": {
            "scope_guard_status": scope_guard_status,
            "secret_scan_status": secret_scan_status,
            "android_checks_status": android_checks_status,
        },
        "changes": {
            "files_changed": len(changed_files),
            "modules_touched": modules,
            "code_modules_touched": code_modules,
        },
        "tests": collect_junit(),
        "snapshots": collect_paparazzi(),
        "agent_kpis": {
            "first_pass_green": all_green,
            "first_pass_green_definition": (
                "scope guard, secret scan and Android checks are all success/skipped in the same CI attempt"
            ),
            "rework_ratio": None,
            "time_to_merge_hours": None,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Collect agent-oriented CI KPI snapshot.")
    parser.add_argument("--output", required=True, help="Path to output JSON file.")
    parser.add_argument("--diff-base", default=os.getenv("DIFF_BASE"), help="Base git ref for change metrics.")
    parser.add_argument("--diff-head", default=os.getenv("DIFF_HEAD"), help="Head git ref for change metrics.")
    args = parser.parse_args()

    payload = build_payload(args)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(f"[OK] KPI snapshot written to {output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
