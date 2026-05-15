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

InferenceProvider
    -> ModelManager
        -> SessionManager
            -> OpenAI Server
            -> Minimal Chat/Test UI

## Current Constraints

- limited Git/GitHub operational experience
- Android internals still to be explored
- runtime lifecycle not yet analyzed
- no local Android CI/CD experience yet

## Operational Workflow

### ChatGPT

Used for:
- orchestration
- architecture
- roadmap
- strategic continuity
- project memory
- review and decomposition

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

## Immediate Priorities

1. Stabilize repository structure
2. Create Android CI workflow
3. Analyze Google Garden architecture
4. Build first reproducible APK
5. Create minimal fake API server
6. Integrate runtime progressively

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
