# Agent Workspace

This directory contains reusable task briefs, role-specific instructions, and execution templates for coding agents.

AGENTS.md remains the general contract.
Files in this directory provide more specific operational guidance.

## Files

| File | Purpose | Status |
|---|---|---|
| bootstrap-phase-0.md | Task brief for completing or reviewing repository bootstrap work | Historical (Phase 0 completed) |
| create-android-skeleton.md | Task brief for creating minimal Android Kotlin project skeleton | Historical (Phase 2 completed) |

Current operational task briefs are the 8 sessions defined in [`fable5/roadmap-sessioni.md`](../fable5/roadmap-sessioni.md).

## Usage Rules

- Read AGENTS.md first.
- Read README.md, docs/project-state.md, docs/roadmap.md, docs/architecture.md, and fable5/roadmap-sessioni.md before proposing architectural or implementation changes.
- Use the specific session brief from `fable5/roadmap-sessioni.md` matching the assigned task.
- If work outside the planned sessions is contemplated, consult `fable5/backlog.md` and obtain project owner approval.
- Treat all implementation plans incrementally based on the actual branch state, avoiding duplicating configurations or setups that have already been committed.

## Agent Roles

### ChatGPT

Acts as orchestrator, reviewer, architectural coordinator, and continuity layer.

### Claude Code

Acts as implementation and repository operation agent.

### Codex

Acts as implementation, refactoring, and code review agent.

## Persistent Output

Important decisions must end up in repository files, not only in chat.
