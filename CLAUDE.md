# CLAUDE.md

## Purpose

This file is the Claude Code entrypoint for the Android Edge LLM Server repository.

Claude Code must treat AGENTS.md as the general operating contract and this file as the Claude-specific entrypoint.

## Required Startup Reading

Before making changes, read:

1. README.md
2. docs/project-state.md
3. docs/roadmap.md
4. docs/architecture.md
5. AGENTS.md
6. .agents/README.md
7. the task-specific file under .agents/, when provided

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
