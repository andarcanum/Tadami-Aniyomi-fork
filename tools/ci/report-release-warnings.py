#!/usr/bin/env python3
"""Summarize warning groups from a Gradle/R8 UTF-16 log.

Usage:
  python tools/ci/report-release-warnings.py r8_minify_release_after_proguard.log
  python tools/ci/report-release-warnings.py r8_minify_release_after_proguard.log --enforce
"""

from __future__ import annotations

import argparse
import re
from collections import Counter
from pathlib import Path

ALLOWED_MAX = {
    "kotlin_metadata": 0,
    "field_rule": 1,
    "context_receivers_warning": 0,
    "aapt_non_positional_format": 0,
    "missing_class": 0,
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("log_file", help="Path to a Gradle/R8 log file")
    parser.add_argument(
        "--enforce",
        action="store_true",
        help="Exit non-zero if warning counts exceed expected maxima",
    )
    return parser.parse_args()


def read_log_lines(log_path: Path) -> list[str]:
    raw = log_path.read_bytes()
    if raw.startswith(b"\xff\xfe") or raw.startswith(b"\xfe\xff"):
        return raw.decode("utf-16", errors="ignore").splitlines()
    if raw.startswith(b"\xef\xbb\xbf"):
        return raw.decode("utf-8-sig", errors="ignore").splitlines()
    try:
        return raw.decode("utf-8").splitlines()
    except UnicodeDecodeError:
        return raw.decode("cp1252", errors="ignore").splitlines()


def main() -> int:
    args = parse_args()

    log_path = Path(args.log_file)
    if not log_path.exists():
        print(f"Log file not found: {log_path}")
        return 2

    lines = read_log_lines(log_path)

    metadata_count = sum(
        "An error occurred when parsing kotlin metadata" in line for line in lines
    )
    field_rule_count = sum("is used in a field rule" in line for line in lines)
    context_receivers_count = sum(
        "Experimental context receivers are superseded by context parameters" in line
        for line in lines
    )
    aapt_non_positional_count = sum(
        "Multiple substitutions specified in non-positional format of string resource"
        in line
        for line in lines
    )

    missing_re = re.compile(r"Missing class\s+([^\s]+)")
    missing_classes: list[str] = []
    for line in lines:
        match = missing_re.search(line)
        if match:
            missing_classes.append(match.group(1))

    aapt_key_re = re.compile(r"string/([A-Za-z0-9_]+)")
    aapt_keys = Counter()
    for line in lines:
        if (
            "Multiple substitutions specified in non-positional format of string resource"
            in line
        ):
            match = aapt_key_re.search(line)
            if match:
                aapt_keys[match.group(1)] += 1

    counts = {
        "kotlin_metadata": metadata_count,
        "field_rule": field_rule_count,
        "context_receivers_warning": context_receivers_count,
        "aapt_non_positional_format": aapt_non_positional_count,
        "missing_class": len(missing_classes),
    }

    print(f"log_file={log_path}")
    for key, value in counts.items():
        print(f"{key}={value}")

    if missing_classes:
        print("\nmissing_class_list:")
        for clazz, count in Counter(missing_classes).most_common():
            print(f"  {count} {clazz}")

    if aapt_keys:
        print("\naapt_top_keys:")
        for key, count in aapt_keys.most_common(20):
            print(f"  {count} {key}")

    if args.enforce:
        violations: list[tuple[str, int, int]] = []
        for key, allowed_max in ALLOWED_MAX.items():
            actual = counts[key]
            if actual > allowed_max:
                violations.append((key, actual, allowed_max))
        if violations:
            print("\nwarning_regressions:")
            for key, actual, allowed_max in violations:
                print(f"  {key}: actual={actual}, allowed_max={allowed_max}")
            return 3

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
