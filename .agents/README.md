# Agent Workspace

This directory contains reusable task briefs, role-specific instructions, and execution templates for coding agents.

AGENTS.md remains the general contract.
Files in this directory provide more specific operational guidance.

## Files

| File | Purpose |
|---|---|
| bootstrap-phase-0.md | Task brief for completing or reviewing repository bootstrap work |

## Usage Rules

- Read AGENTS.md first.
- Read README.md, docs/project-state.md, docs/roadmap.md, and docs/architecture.md before proposing architectural or implementation changes.
- Use the specific task brief that matches the current work.
- If no task brief matches, propose a small new brief instead of improvising a broad task.
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
