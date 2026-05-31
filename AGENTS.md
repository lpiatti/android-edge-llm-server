# AGENTS.md

## Purpose

This file is the general operating contract for coding agents working on Android Edge LLM Server.

It applies to all agents unless a more specific instruction file overrides it for a given tool.

> [!IMPORTANT]
> **Environment Constraint**: The local runner host does NOT have a Java, Gradle, or Android SDK development environment installed.
> Do NOT execute or try to run local gradle commands (e.g., `./gradlew assembleDebug` or `./gradlew test`).
> All code compilation, testing, and APK validation are performed exclusively via **GitHub Actions CI/CD** on pull requests and pushes to feature branches.

## Mandatory Startup Protocol

At the beginning of every new agent session on this repository, before proposing plans or changing files, the agent must read the real repository contents listed below and then present a short startup status block.

Required reading order:

1. README.md
2. docs/project-state.md
3. docs/roadmap.md
4. docs/architecture.md
5. docs/index.md
6. AGENTS.md
7. .agents/README.md
8. the task-specific file under .agents/, when one is provided
9. tool-specific instruction files such as CLAUDE.md or CODEX.md, when relevant

The startup status block must contain:

```text
Repository context:
- Branch/worktree checked:
- Files actually read:
- Current project phase:
- Current objective:
- Relevant constraints: (Include local host environment constraints here!)
- Intended task:
- Proposed branch:
- First validation command: GitHub Actions CI (no local Gradle available)
```


Rules:

- Do not make file changes before presenting the startup status block.
- Do not infer missing file contents from memory or conventions.
- If a required file is missing, report it explicitly and stop unless the task is to create it.
- If repository content conflicts with chat history, use repository content.

## Source of Truth

Repository files are authoritative in this order:

1. README.md
2. docs/project-state.md
3. docs/roadmap.md
4. docs/architecture.md
5. docs/index.md
6. AGENTS.md
7. Files under .agents/
8. Tool-specific instruction files such as CLAUDE.md or CODEX.md

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
9. Present the mandatory startup status block before editing files.

## Branch and PR Rules

Agents must not commit directly to main unless explicitly instructed.

Default workflow (Guided PR & CI/CD Validation):

1. **Create a feature branch** from main.
2. **Make the smallest useful change** in the local workspace.
3. **Commit changes** locally with a descriptive message.
4. **Push the branch** to the remote origin.
5. **Halt and Guide the User**: Since local builds are unavailable and GitHub CI runs only on pull requests, the agent MUST stop at this point. The agent MUST provide clear, step-by-step instructions to the user on how to open a Pull Request against `main` on GitHub to trigger the compilation.
6. **Wait for CI Feedback**: The agent must ask the user to report the outcome of the GitHub Actions build (successful APK generation or compilation errors) before suggesting a merge or proceeding to further steps.

For implementation tasks, the task brief should define the branch name.
If no branch name is defined, stop and ask for one.

Direct commits to main are acceptable only for explicit repository-bootstrap maintenance performed by the project orchestrator.

## Change Discipline

Before making changes, agents should identify:

- current roadmap phase
- files expected to change
- reason for each change
- risks or unknowns

During implementation, agents MUST follow these strict guidelines:

* **Incremental Delta Principle**: Before writing implementation plans or editing files, the agent MUST inspect the actual current filesystem and branch files. When expanding a branch or adding secondary features on top of a previous step, treat all existing configurations, dependencies, and code files as already active. Propose plans and edits only as an incremental delta, strictly avoiding plans to recreate or re-add already active setups.
* **Documentation Sync**: At the end of every successful iteration or milestone completion, the agent MUST update `docs/project-state.md` and `docs/roadmap.md` to keep the repository's status 100% accurate.

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
- GitHub CI Run: [Link or status of the action run]
- Result: ...

Open issues:
- ...

Next recommended step:
- ...
```
