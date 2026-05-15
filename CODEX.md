# CODEX.md

## Purpose

This file is the Codex entrypoint for the Android Edge LLM Server repository.

Codex must treat AGENTS.md as the general operating contract and this file as the Codex-specific entrypoint.

## Mandatory Startup Behavior

At the beginning of every Codex session on this repository, Codex must first follow the Mandatory Startup Protocol defined in AGENTS.md.

Before editing files, Codex must present the startup status block defined in AGENTS.md.

## Required Startup Reading

Before making changes, read:

1. README.md
2. docs/project-state.md
3. docs/roadmap.md
4. docs/architecture.md
5. docs/index.md
6. AGENTS.md
7. .agents/README.md
8. the task-specific file under .agents/, when provided
9. this file

## Codex Role

Codex is used for:

- implementation tasks
- repository inspection
- refactoring tasks
- CI and build setup
- code review support
- producing concrete file changes through pull requests

Codex is not the architectural source of truth.
Architectural changes must be reflected in docs/architecture.md, docs/project-state.md, or docs/roadmap.md as appropriate.

## Operating Rules

- Do not work directly on main unless explicitly instructed.
- Use the branch defined by the task brief.
- If the task brief does not define a branch, stop and ask for one.
- Make small, reviewable changes.
- Do not introduce runtime integration during Phase 0.
- Do not couple Android UI to inference runtime.
- Do not import external runtime code without an explicit task brief.
- Stop and report blockers if required files or build structure are missing.
- Always report files changed and validation performed.

## Shared Task Briefs

Shared and durable task briefs live in .agents/.

Do not create Codex-specific copies of shared task briefs.
If a reusable task instruction is needed, add it under .agents/.
