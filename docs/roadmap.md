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

## Phase 5 — Server Stabilization

Status: IN PROGRESS

Goals:
- background execution stability
- Android lifecycle robustness
- foreground service management
- memory management

Deliverables:
- stable server mode
- long-running inference sessions
- improved runtime resilience

---

## Phase 6 — Edge Extensions & Compatibility

Status: PLANNED

Goals:
- Local Text-To-Speech (TTS) endpoint (`/v1/audio/speech`) using native Android TTS engine or lightweight models.
- Local Embeddings endpoint (`/v1/embeddings`) via compact ONNX/LiteRT models (such as BERT or MiniLM) for offline LAN RAG.
- OpenAI Response Schema Compliance: Ensure complete matching of all JSON response structures with OpenAI's official specifications.
- Curated Model Hub Manager: In-app searchable UI list pulling stable model metadata from the Hugging Face [litert-community](https://huggingface.co/litert-community) repository, with automatic downloads, resume capabilities, and SHA256 verification.
- Lightweight Local Image Generation (`/v1/images/generations`) leveraging compact models (such as TinySD) with RAM safety checks.

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

