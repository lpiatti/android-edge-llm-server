# Roadmap

## Phase 0 — Repository Bootstrap

Status: COMPLETED

Goals:
- initialize repository
- define architecture direction
- establish operational workflow
- prepare documentation structure

Deliverables:
- README
- roadmap
- architecture notes
- project-state
- GitHub Actions bootstrap

---

## Phase 1 — Garden Analysis

Status: COMPLETED

Goals:
- understand Google Garden architecture
- identify reusable runtime components
- understand model lifecycle
- understand token streaming
- identify UI/runtime coupling points

Deliverables:
- architecture analysis document
- runtime component map
- dependency map
- integration strategy notes

---

## Phase 2 — Minimal Android Build

Status: COMPLETED

Goals:
- compile reproducible APK
- validate Android toolchain
- validate GitHub Actions Android pipeline
- validate artifact generation

Deliverables:
- working CI pipeline
- downloadable APK artifact
- first successful cloud build

---

## Phase 3 — Minimal Server Skeleton

Status: COMPLETED

Goals:
- embedded HTTP server
- health endpoint
- fake OpenAI-compatible endpoint
- validate Android background lifecycle

Initial Endpoints:
- GET /health
- GET /v1/models
- POST /v1/chat/completions

Deliverables:
- fake response server
- local network accessibility
- API compatibility tests

---

## Phase 4 — Runtime Integration

Status: COMPLETED

Goals:
- [x] Milestone 4.1: Integrate LiteRT-LM dependency, configure ProGuard rules, upgrade coroutines/Ktor to 3.0.3, stabilize foreground service across SDKs, and build in-app crash reporter.
- [x] Milestone 4.2: Implement local public folder scanning, write `InferenceProvider` abstraction layer, load local quantized `.litertlm` models, and stream real token generation.
- [x] Milestone 4.3: UI Refactoring & System File Picker. Add a sticky status island, re-order and re-label tabs (Model Engine, Server Daemon, Test Suite), add collapsible diagnostic console logs, enforce state interlocks, and replace the storage directory scanner with a native System File Picker (`ACTION_OPEN_DOCUMENT`).

Deliverables:
- [x] Aligned build configuration and stable background server running Ktor 3.0.3 and LiteRT-LM on physical devices.
- [x] Local models directory scanner (Broad fallback scanner) inside MainActivity.
- [x] Real token generation and inference abstraction.
- [x] Streaming HTTP responses (OpenAI SSE chunk flows and Ollama stream JSON lines).
- [x] Collapsible monospaced log windows, always-visible status island, and native android file picker for `.litertlm` files.

---

## Phase 5 — Server Stabilization & API Semantics

Status: IN PROGRESS

Operational plan: [`fable5/roadmap-sessioni.md`](../fable5/roadmap-sessioni.md) (Sessions S1–S8)
- S1: Correct API semantics (full `messages` array context + sampling parameters)
- S2: RequestQueue (single-worker FIFO serialization, HTTP 429 rejection)
- S3: Safe model loading (RAM pre-flight, crash marker, explicit errors)
- S4: Ollama API complete & optional API key authentication
- S5: Tool calling foundation (request parser, prompt template, tool_calls output)
- S6: Tool calling streaming & agent harness validation
- S7: Static Web UI served by Ktor (browser control surface)
- S8: Consolidation release (bugfixes, doc sync, tagged APK release)

---

## Phase 6 — Edge Extensions & Backlog

Status: PLANNED

Backlog and future extensions: see [`fable5/backlog.md`](../fable5/backlog.md) (Embeddings, Multi-engine providers, In-app Model Hub, Multi-node federation, Phone-side tools, TTS endpoint).

Non-Goals / Excluded:
- GGUF/llama.cpp support: Excluded (KO) to maintain microscopic binary size and avoid complex C++ NDK/JNI compilations.

---

## Long-Term Direction

Potential future directions:
- multiple runtime providers
- LAN discovery
- agent integration
- lightweight orchestration
- tool calling
- multimodal support
- distributed edge nodes


