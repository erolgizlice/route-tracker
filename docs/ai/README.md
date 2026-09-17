# How AI was used

This project was written with **Claude Code** running **Claude Opus 5**, in one working session from
2026-09-16 to 2026-09-17. A second, separate Claude Code session acted as an independent reviewer. Every
commit carries a `Co-Authored-By: Claude Opus 5` trailer.

## Workflow

1. **Brief.** The session opened with a hand-off brief: the case text, a build stack already verified in a
   throwaway probe project, a module plan, and lessons from an earlier location-tracking app
   ([`prompts.md`](prompts.md), message 1). The brief was prepared in the separate reviewer session (step 5),
   and the author decided what to send.
2. **Ask before deciding.** When a choice belonged to the author, the assistant asked a multiple-choice
   question before acting. There were ten: module layout, commit attribution (`Co-Authored-By`), repository
   visibility, repository name and location, what the first fix means, the accuracy threshold, approximate-
   only permission, reset while tracking, which emulator to use, and what happens to a lost session. The
   author answered all of them, and the answers are recorded as decisions in
   [`docs/decisions.md`](../decisions.md).
3. **Implement in small commits** (Conventional Commits).
4. **Verify before claiming.** Build and test claims go through the
   [`verify-claim`](../../.claude/skills/verify-claim/SKILL.md) skill: delete old results, run without caches,
   read logs and XML, and mutate the code to prove a test can fail. On-device claims go through the
   [`device-check`](../../.claude/skills/device-check/SKILL.md) skill. Results, including attempts that turned
   out to be invalid, go to the verification log in `docs/decisions.md`.
5. **Independent re-measurement.** At each milestone, the reviewer session re-ran the claims itself: test
   counts, mutations, a scan for sensitive terms, and on-device behaviour. Its findings reached the working
   session as the author's messages ([`prompts.md`](prompts.md), messages 3–7, 10, 11 and 13) and were fixed
   in follow-up commits. Those messages, like the delivery brief (message 12) and the two briefs for the
   final round (messages 16 and 17), were prepared in the reviewer session, and the author decided what to
   send.
6. **Human review before publishing.** Until the morning of 2026-09-17 the assistant also pushed. From then on
   it only committed; the author reviewed each commit locally and pushed.

## What verification caught

Each of these was written or claimed first and corrected after a measurement or a review:

- The camera never centered on a route recorded after a fresh install (review 1).
- Unit tests could not catch a wrong anchor query: `ORDER BY id DESC` → `ASC` passed all 25 tests. An
  instrumented Room test now fails on it (D10).
- A stated reason for the service start order contradicted a measured source (review 3). The order was
  re-justified in D17.
- After an approximate-only grant, the app offered "Open settings" although Android 16 would still show the
  upgrade dialog. It was measured on a device and fixed (D18).
- An app crash on emulators with older Google Play services, found on the first emulator launch and confirmed
  by mutation (D20).
- False results in the assistant's own tooling: stale test XML, a zsh glob that silently turned a grep into
  "zero matches", instrumented test results missing from a summary script, and four invalid demo recordings.
- Two claims in a commit message about a map with no key and a map with no tiles, both written before they
  were measured and both wrong; corrected in the same commit (D24).
- A marker tap test that looked like a defect until the test itself was measured: the seeded route had points
  66 m apart, closer than the 100 m rule can produce, and repeated taps at the identical pixel are dropped by
  `adb shell input tap` (D23).

## Helper files

| File | Purpose |
|---|---|
| [`CLAUDE.md`](../../CLAUDE.md) | Instructions loaded by the assistant in this repository: modules and boundaries, invariants of the 100 m rule, tracking service rules, working rules (measure before claiming, commit but do not push, never commit device media) |
| [`.claude/skills/verify-claim/`](../../.claude/skills/verify-claim/SKILL.md) | Procedure for build, test and mutation claims; `junit_summary.py` reads JUnit XML from JVM and instrumented runs and flags stale files |
| [`.claude/skills/device-check/`](../../.claude/skills/device-check/SKILL.md) | Procedure for devices and emulators: permissions, foreground service state, process death, force-stop, revocation, notifications, emulator commands, recording a demo; `droid.py` drives the UI through `uiautomator` |
| [`docs/decisions.md`](../decisions.md) | Decisions D1–D24 with rejected alternatives and evidence labels (Measured, Reasoned, Device check pending), plus the verification log |
| [`docs/stress/README.md`](../stress/README.md) | How a thousand markers were measured, with the exact commands, the frame statistics of every run, and what the numbers do not say |
| [`docs/ai/prompts.md`](prompts.md) | The author's messages to the session, in Turkish, each with an English summary |

## What is not included

- **Local configuration:** `local.properties` (SDK path and Maps key) and `.claude/settings.local.json`
  (machine-specific permissions).
- **The raw session transcript.** It contains assistant output, tool output from the author's machine, and
  content that is not about the code. `prompts.md` keeps the author's messages, with that content cut and
  every cut marked `[removed: …]`.
- **Media from the physical test phone.** Screenshots from a real device show a real location. All media in
  `docs/media/` comes from an emulator on a made-up route.
