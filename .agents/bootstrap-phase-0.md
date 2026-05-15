# Bootstrap Phase 0 Task Brief

## Objective

Complete or review the repository bootstrap phase without introducing application runtime logic.

## Context

The project aims to become an Android-native edge AI server exposing local LLM inference through OpenAI-compatible APIs.

During Phase 0, the focus is repository structure, documentation, reproducibility preparation, and agent workflow setup.

## Allowed Work

- Documentation structure
- Architecture notes
- Agent instruction files
- CI skeleton
- Minimal repository scaffolding when explicitly requested
- Project state updates

## Not Allowed in Phase 0

- Real LLM runtime integration
- Garden code import
- Token streaming implementation
- Broad Android UI implementation
- Large refactors
- Premature multi-provider framework implementation

## Required Reading

Before working, read:

1. README.md
2. docs/project-state.md
3. docs/roadmap.md
4. docs/architecture.md
5. AGENTS.md
6. .agents/README.md

## Task Checklist

1. Inspect current repository structure.
2. Verify required bootstrap files exist.
3. Report missing files before creating them.
4. Create or update only the smallest necessary set of files.
5. Avoid changing roadmap phases unless deliverables are actually complete.
6. Update docs/project-state.md if the repository state changes.
7. Produce an end-of-task report.

## Validation

If a build system exists, run the most relevant lightweight validation command.

If no build system exists yet, state that build validation is not applicable and explain what is missing.

Do not claim that CI or build is working unless it was actually executed or clearly configured.

## End Report Template

```text
Task result: OK or KO

Summary:
- ...

Files changed:
- ...

Validation:
- ...

Open issues:
- ...

Next recommended step:
- ...
```
