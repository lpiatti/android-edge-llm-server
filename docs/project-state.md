# Project State

## Current Objective

Create an Android-native edge AI server capable of exposing local LLM inference through OpenAI-compatible APIs.

The initial focus is NOT feature completeness.
The initial focus is:

- understanding architecture
- reproducible builds
- clean project structure
- long-term maintainability
- agent-friendly workflows

## Strategic Direction

The project should evolve toward:

- lightweight Android-native inference server
- OpenAI-compatible API surface
- reusable inference abstraction layer
- minimal UI
- server-oriented lifecycle
- future multi-provider runtime support

The project should avoid becoming:

- a heavily modified fork difficult to sync
- a chat-centric Android app
- a UI-heavy assistant application

## Expected Architecture Direction

Inference runtime and UI should be decoupled.

Target conceptual architecture:

```text
InferenceProvider
    -> ModelManager
        -> SessionManager
            -> OpenAI-compatible Server
            -> Minimal Chat/Test UI
```

The initial architecture document is maintained in docs/architecture.md.

## Current Constraints

- limited Git/GitHub operational experience
- Android internals still to be explored
- runtime lifecycle not yet analyzed
- no local Android CI/CD experience yet
- Android Gradle project structure not yet created
- real Android APK build not yet configured

## Operational Workflow

### ChatGPT

Used for:
- orchestration
- architecture
- roadmap
- strategic continuity
- project memory
- review and decomposition
- initial general structure when useful before delegating to coding agents

### Claude Code / Codex

Used for:
- implementation
- refactoring
- repository analysis
- operational coding

### GitHub

Used as:
- persistent project memory
- documentation source of truth
- CI/CD platform
- artifact storage

### VSCode

Used as:
- local cockpit
- repository editing environment
- Git integration environment

## Agent Instruction Structure

Agent instructions are intentionally separated from product documentation.

Current structure:

| Path | Purpose |
|---|---|
| AGENTS.md | General operating contract for all coding agents |
| .agents/README.md | Shared agent workspace overview |
| .agents/bootstrap-phase-0.md | Bootstrap Phase 0 task brief |
| CLAUDE.md | Claude Code entrypoint |
| .claude/README.md | Claude Code specific workspace notes |

## Immediate Priorities

1. Stabilize repository structure
2. Create initial Android Gradle project skeleton
3. Create Android CI workflow with real build once skeleton exists
4. Analyze Google Garden architecture
5. Build first reproducible APK
6. Create minimal fake API server
7. Integrate runtime progressively

## Bootstrap Progress

Completed:

- documentation index created in docs/index.md
- initial architecture document created in docs/architecture.md
- general agent contract created in AGENTS.md
- shared agent workspace created in .agents/
- Claude Code entrypoint created in CLAUDE.md
- Claude Code workspace created in .claude/
- bootstrap GitHub Actions check created in .github/workflows/bootstrap-check.yml

Still open:

- create actual Android project skeleton
- replace bootstrap check with or extend it into real Android CI build
- produce first APK artifact
- perform Garden architecture analysis

## Important Development Philosophy

Prefer:
- incremental evolution
- isolated modules
- minimal viable implementations
- reproducible builds
- low coupling

Avoid:
- premature optimization
- massive rewrites
- uncontrolled merges from forks
- architecture drift
- large undocumented agent-generated changes
