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
- **Stability Rules:** Enforced by [daemon-stability-guidelines.md](daemon-stability-guidelines.md).

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

The operational plan for the current phase is `fable5/roadmap-sessioni.md` (consultancy
outcome, 2026-07-05): sessions S0–S8, each with explicit scope and verifiable acceptance
criteria. Next up:

1. S0 — Documentation alignment (`fable5/correzioni-doc.md`)
2. S1 — Correct API semantics: process the full `messages` array, parameter passthrough, prefill benchmark
3. S2 — Request queue (serialize inference, 429 on overflow)

Everything beyond S8 is declared backlog in `fable5/backlog.md`.

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
- Phase 4 (Milestone 4.1): LiteRT-LM dependency integration (`litertlm-android:0.11.0`) completed, R8/ProGuard keep rules configured, Kotlin compiler upgraded to `2.2.0`, and Ktor engine upgraded to `3.0.3` to solve binary coroutine version conflicts. Background Foreground Service stabilized with backward-compatible SDK guards (Android 10-13 backport), dynamic startup notification permissions, and a local in-app crash logger that displays system stacktraces directly inside the terminal console.
- Phase 4 (Milestone 4.2): Implemented directory scanning via `MANAGE_EXTERNAL_STORAGE` (All Files Access) on target public folders, created clean `InferenceProvider`/`ModelManager` abstraction layer to support dynamic mock vs real `.litertlm` model loading/swapping, integrated completions and tag routes with support for OpenAI/Ollama event-stream async streaming response collections, and designed a premium programmatic Tab 3 Model Manager UI dashboard equipped with local quick token flow inference testing.
- Phase 4 (Milestone 4.2.1): Resolved layout duplicate view runtime exception and migrated system crash log writing and reading paths to the robust internal `filesDir` storage, executing early in MainActivity `onCreate` to ensure early diagnostics are shown upon app reopen.
- Phase 4 (Milestone 4.3): Completed layout refactoring (Model Engine, Daemon, Test Suite, Logs), added collapsible system log viewers, implemented a sticky status island displaying active model and server address telemetry, added a live statistics dashboard card, secured state interlocks to prevent load overflows, replaced local scans with a native Storage Access Framework (SAF) system document picker, resolved native GPU library linker locks in the manifest, and integrated a persistent Spotify-style FGS notification stop action.

Still open / In Progress:

- Phase 5: Server Stabilization and background daemon execution audits (memory optimization, wake lock lifecycle robustness, and Foreground Service persistence).
- Phase 6: Edge Extensions & Compatibility (Local TTS, Local Embeddings, OpenAI response schemas alignment, Model Hub Manager, TinySD image generation).



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
