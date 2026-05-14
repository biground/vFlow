# vFlow Local Agent Bootstrap

This file is intentionally a thin local shell. Formal project guidance lives in
`skills/vflow-development/` and is ignored by Git in this working copy.

## Session Start

1. Read `skills/vflow-development/SKILL.md`.
2. Read `skills/vflow-development/routing.yaml`.
3. Match the user's current task to the route before editing code.
4. If the task changes after compaction, interruption, or a long debugging loop,
   repeat the routing step.

## Priority

1. User's latest explicit instruction.
2. Route-specific files from `skills/vflow-development/routing.yaml`.
3. General repository rules in `skills/vflow-development/SKILL.md`.

## Completion Flow

After making code changes, Codex must self-review the diff, run the appropriate
verification, then build and install the package on the currently connected
device. Use `skills/vflow-development/workflows/package-and-install.md` for the
exact JDK, Gradle, APK, ADB install, and package confirmation commands. Do not
wait for user review before packaging; "review" means Codex's own code review.

## Commit Discipline

After any requested modification, Codex should commit only the content changed
for the current task. The commit message must clearly describe what was changed
and follow the user's Chinese Conventional Commits rule.

## Local-Only Status

These files are local collaboration scaffolding and should remain untracked:

- `AGENTS.md`
- `.cursor/skills/vflow-development/`
- `skills/vflow-development/`
