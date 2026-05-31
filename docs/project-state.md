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
- **No local Gradle/Android SDK/Java environment**: The local runner host machine does not have build tools. All compilations and build validations must be performed exclusively via GitHub Actions CI/CD.

## Target Platform & Environment Profile

- **Minimum SDK:** API 29 (Android 10.0) — allows repurposing older devices.
- **Target SDK:** API 34 (Android 14.0) — complies with modern security and foreground service requirements.
- **Deployment Profile:** Dedicated server mode (permanently on AC power, dedicated high-perf Wi-Fi, low concurrent app usage).
- **Stability Rules:** Enforced by [daemon-stability-guidelines.md](file:///c:/python_sources/android-edge-llm-server/docs/daemon-stability-guidelines.md).

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

1. Implement in-app Bottom Tab Navigation (MainActivity UI)
2. Expand LlmServerService with local Ktor Ollama mock endpoints (/api/tags and /api/chat)
3. Create interactive Dynamic API Client Test Suite inside Tab 2
4. Perform Garden repository architectural analysis (Phase 1)
5. Integrate real runtime progressively (Phase 4)

## Bootstrap Progress

Completed:

- Documentation index created in docs/index.md
- Initial architecture document created in docs/architecture.md
- General agent contract created in AGENTS.md (featuring CI validation and incremental rules)
- Shared agent workspace created in .agents/
- Claude Code and Codex entrypoints created in CLAUDE.md and CODEX.md
- Created actual Android project skeleton (Gradle files, Manifest, Activity)
- Replaced bootstrap check with a real, comprehensive Android CI build (.github/workflows/android-ci.yml)
- Produced, compiled, and verified the first Fake API APK on a physical phone via GitHub Actions CI
- Phase 3: Minimal Server Skeleton & Dynamic Test Harness (Mock OpenAI/Ollama endpoints, Bottom Tab UI, network bind interface selector, and raw diagnostic HTTP console dumps completed)
- Phase 1: Garden Architecture Analysis completed (see docs/garden-analysis.md)

Still open / In Progress:

- Integrate Google AI Edge runtime, load quantized model, and stream real token generation (Phase 4)


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
