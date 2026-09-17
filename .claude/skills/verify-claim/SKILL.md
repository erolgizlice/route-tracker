---
name: verify-claim
description: Prove a build, test, or mutation result before stating it in a commit message, in docs/decisions.md, or in chat. Use whenever you are about to claim "N tests pass", "the build is green", "this test catches X", or "there are zero matches".
---

# verify-claim

A green build or a zero count is not evidence until you have shown the check could have failed.
Every step below exists because skipping it produced a false result in this project; see the
verification log in `docs/decisions.md`.

**Run steps 1–4 in a single Bash call.** In Claude Code every Bash call starts a new shell, so `$START`
and `$LOG` do not carry over to the next call.

**Scope:** JVM unit test results are written to `<module>/build/test-results/`. Instrumented results are
written to `<module>/build/outputs/androidTest-results/connected/<variant>/TEST-<device>.xml`, a path measured
on 2026-09-17 after the script had reported 25 JVM tests and silently ignored 6 instrumented ones. Steps 1
and 4 cover both locations.

Connected tests run on **every** attached device. Pin one with `ANDROID_SERIAL`:
`ANDROID_SERIAL=emulator-5554 ./gradlew :data:connectedDebugAndroidTest`.

## 1. Delete old results

Stale JUnit XML once reported the previous run's failure as the current result.

```bash
find . \( -path '*/build/test-results' -o -path '*/build/outputs/androidTest-results' \) -prune -exec rm -rf {} +
find . \( -path '*/build/test-results' -o -path '*/build/outputs/androidTest-results' \) | wc -l    # must print 0
```

Use `find`, not `rm -rf */build/test-results`. In zsh an unmatched glob aborts the whole command:
nothing is deleted and nothing says so.

## 2. Run without caches

```bash
START=$(date +%s); LOG=$(mktemp)
./gradlew :core:test --rerun-tasks --no-build-cache --console=plain > "$LOG" 2>&1
```

## 3. Read the log, not the exit code

```bash
grep -E '^e: |FAILED|BUILD (SUCCESSFUL|FAILED)|actionable tasks' "$LOG"
grep -c 'FROM-CACHE' "$LOG"                     # must print 0
```

Expect `N actionable tasks: N executed`. A task marked `UP-TO-DATE`, `FROM-CACHE` or `NO-SOURCE` did not run.

## 4. Read the XML with a parser, and reject stale files

```bash
python3 .claude/skills/verify-claim/junit_summary.py "$START"
```

It prints per-suite counts and failed test names, and marks any file older than `$START` as `STALE`.
Quote only totals from a run with `stale_files=0`.

## 5. Mutation check: can the test fail?

1. Copy the file to a scratch location.
2. Confirm the text to change occurs **exactly once** in the file.
3. Apply the change, then run steps 1–4.
4. Confirm it **compiled**: no `e: ` lines in the log. A mutation that does not compile proves nothing,
   and the old XML it leaves behind looks like a real result.
5. Confirm that the intended tests fail, and only those.
6. Restore from the copy and check the checksums are equal (`md5 -q` on macOS, `md5sum` on Linux).
7. Run steps 1–4 again on the restored code: everything passes. Check that `git status --short` is clean.

## Rules

- **A zero counts only after a positive control.** Before trusting "0 matches" or "0 failures", show the
  same command returning non-zero on something that exists. Quote globs: `grep --include='*.kt'`.
- **A probe counts only if it passes on the original code.** A test asserting a point exactly 100 m away
  fails on the unmodified gate, because the haversine returns 99.99999999969069 m. Its failure under a
  mutation would prove nothing.
- **Scope the claim to what ran.** Tests against a fake repository say nothing about the Room query
  behind it (D10).
- Record the result in the verification log of `docs/decisions.md`, including attempts that turned out
  to be invalid.
