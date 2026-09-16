#!/usr/bin/env python3
"""Summarise Gradle JUnit XML results and flag stale ones.

Usage: junit_summary.py <run-start-epoch-seconds> [project-root]

Exit status is non-zero when no result files exist, when any file predates the run, or when a
test failed, so the output must be read, not just the status.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

COUNTS = ("tests", "failures", "errors", "skipped")


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    try:
        started = float(sys.argv[1])
    except ValueError:
        print(
            f"INVALID RUN START {sys.argv[1]!r}: capture START=$(date +%s) before the Gradle run, "
            "in the same Bash call as this script"
        )
        return 2
    root = sys.argv[2] if len(sys.argv) > 2 else "."

    # JVM unit tests only. Instrumented test results are written elsewhere (see SKILL.md, Scope).
    files = sorted(glob.glob(os.path.join(root, "**/build/test-results/**/TEST-*.xml"), recursive=True))
    if not files:
        print("NO RESULT FILES under build/test-results: the tests did not run, or wrote their results elsewhere")
        return 1

    totals = dict.fromkeys(COUNTS, 0)
    stale = 0
    for path in files:
        fresh = os.path.getmtime(path) >= started
        stale += 0 if fresh else 1
        suite = ET.parse(path).getroot()
        counts = {key: int(suite.get(key, 0)) for key in COUNTS}
        for key in COUNTS:
            totals[key] += counts[key]
        label = "fresh" if fresh else "STALE"
        print(f"{label}  {suite.get('name')}: " + " ".join(f"{k}={v}" for k, v in counts.items()))
        for case in suite.iter("testcase"):
            if case.find("failure") is not None or case.find("error") is not None:
                print(f"       FAILED: {case.get('name')}")

    print("TOTAL  " + " ".join(f"{k}={v}" for k, v in totals.items()) + f" stale_files={stale}")
    return 1 if stale or totals["failures"] or totals["errors"] else 0


if __name__ == "__main__":
    sys.exit(main())
