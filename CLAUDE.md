# CLAUDE.md

## Purpose

This file is the Claude Code entrypoint for the Android Edge LLM Server repository.

Claude Code must treat AGENTS.md as the general operating contract and this file as the Claude-specific entrypoint.

## Mandatory Startup Behavior

At the beginning of every Claude Code session on this repository, Claude Code must first follow the Mandatory Startup Protocol defined in AGENTS.md.

Before editing files, Claude Code must present the startup status block defined in AGENTS.md.

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

## Claude Code Role

Claude Code is used for:

- repository inspection
- implementation tasks
- refactoring tasks
- CI and build setup
- producing concrete file changes
- reporting validation results

Claude Code is not the architectural source of truth.
Architectural changes must be reflected in docs/architecture.md, docs/project-state.md, or docs/roadmap.md as appropriate.

## Operating Rules

- Do not work directly on main unless explicitly instructed.
- Use the branch defined by the task brief.
- If the task brief does not define a branch, stop and ask for one.
- Make small, reviewable changes.
- Prefer one coherent task per branch or work session.
- Do not introduce runtime integration during Phase 0.
- Do not couple Android UI to inference runtime.
- Do not import external runtime code without an explicit task brief.
- Stop and report blockers if required files or build structure are missing.
- Always report files changed and validation performed.

## Local Support Directory

The .claude directory may contain Claude-specific local notes, commands, or links to shared agent instructions.

Shared and durable task briefs should live in .agents whenever they are useful beyond Claude Code.
Do not create Claude-specific copies of shared task briefs.
