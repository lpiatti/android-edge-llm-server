# AGENTS.md

## Purpose

This file is the general operating contract for coding agents working on Android Edge LLM Server.

It applies to all agents unless a more specific instruction file overrides it for a given tool.

## Source of Truth

Repository files are authoritative in this order:

1. README.md
2. docs/project-state.md
3. docs/roadmap.md
4. docs/architecture.md
5. AGENTS.md
6. Files under .agents/
7. Tool-specific instruction files such as CLAUDE.md

If repository content conflicts with chat history, use repository content.
If a required file is missing, report it instead of inferring its contents.

## Project Goal

Build an Android-native edge AI server capable of exposing local LLM inference through OpenAI-compatible APIs.

The project should evolve toward a lightweight Android-native server runtime, not a chat-centric Android application.

## Operating Rules

1. Prefer the smallest useful change.
2. Avoid large refactors unless explicitly requested.
3. Keep UI and inference runtime decoupled.
4. Do not introduce runtime integration before the relevant roadmap phase.
5. Preserve reproducible builds.
6. Update persistent documentation when project state changes.
7. Do not hide important decisions only in chat or terminal output.
8. Stop and report blockers instead of inventing missing assumptions.

## Change Discipline

Before making changes, agents should identify:

- current roadmap phase
- files expected to change
- reason for each change
- risks or unknowns

After making changes, agents should report:

- files changed
- build/test commands executed
- results
- open issues
- suggested next step

## Current Phase Guardrail

The current repository state is Phase 0 bootstrap unless docs/roadmap.md says otherwise.

During Phase 0, agents may work on:

- repository structure
- documentation
- CI bootstrap
- agent workflow files
- minimal project scaffolding if explicitly requested

During Phase 0, agents must not work on:

- real LLM runtime integration
- Garden code import
- token streaming implementation
- broad Android UI implementation
- multi-provider runtime framework beyond conceptual documentation

## Architectural Guardrails

Any implementation proposal must be checked against:

| Constraint | Required Check |
|---|---|
| Incrementality | Is this the smallest useful step? |
| Modularity | Does it respect current module boundaries? |
| Low UI/runtime coupling | Does inference remain independent from UI? |
| Upstream alignment | Does it avoid unjustified divergence from upstream runtimes? |
| Build reproducibility | Can the result be built in a repeatable way? |
| Persistent documentation | Does project-state or roadmap need an update? |
| Roadmap coherence | Is the change aligned with docs/roadmap.md? |

## Commit and PR Expectations

Prefer small commits with clear messages.

Suggested message style:

- Add documentation index
- Add initial architecture document
- Add agent operating contract
- Add Android CI skeleton
- Update project state after bootstrap step

PRs should include:

- summary
- changed files
- validation performed
- known limitations
- next recommended step

## Report Format

At the end of a task, use this format:

```text
Task result: OK or KO

Summary:
- ...

Files changed:
- ...

Validation:
- Command: ...
- Result: ...

Open issues:
- ...

Next recommended step:
- ...
```
