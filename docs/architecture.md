# Architecture

## Purpose

Android Edge LLM Server is intended to become an Android-native edge AI server exposing local LLM inference through OpenAI-compatible APIs.

The architecture must stay server-oriented. It must not evolve into a chat-centric Android application.

## Architectural Principles

1. Keep inference runtime independent from UI.
2. Keep API compatibility independent from the selected runtime provider.
3. Prefer incremental implementation over large rewrites.
4. Reuse upstream runtime components where possible.
5. Preserve build reproducibility and CI visibility.
6. Document every architectural shift in persistent repository documents.

## Conceptual Target Architecture

```text
InferenceProvider
    -> ModelManager
        -> RequestQueue
            -> OpenAI-compatible Server
            -> Web UI (static, served by Ktor)
            -> Minimal Android UI
```

## Components

### InferenceProvider

Abstracts the underlying inference runtime.

LiteRT-LM (`.litertlm` format, Gemma 4) is the first concrete provider implementation (ADR 12). ONNX Runtime and MediaPipe Tasks GenAI remain candidate backlog providers behind the same unified `InferenceProvider` interface.

Responsibilities:

- expose a stable local interface for model loading and token generation
- hide runtime-specific details (LiteRT-LM C++ bindings, session lifecycle)
- allow future addition of alternate runtime providers behind the common interface

### ModelManager

Owns model lifecycle at application level.

Expected responsibilities:

- model discovery and file picking
- model metadata extraction
- model loading and unloading
- active model tracking
- memory pre-flight checks and model validation rules

### RequestQueue

Serializes inference requests on a single worker queue in FIFO order (ADR 13 and `fable5/architettura-api.md` §4).

Expected responsibilities:

- enqueue incoming inference requests (OpenAI `/v1/chat/completions` and Ollama `/api/chat`)
- process requests strictly sequentially on a dedicated single coroutine worker to prevent concurrent execution crashes and RAM exhaustion
- reject incoming requests with HTTP 429 (`Too Many Requests`) when queue capacity is reached
- isolate request lifecycle from Android UI lifecycle

### OpenAI-compatible Server

Exposes local network endpoints compatible with OpenAI and Ollama clients.

Supported and planned endpoints:

- GET /health
- GET /v1/models
- POST /v1/chat/completions
- GET /api/version
- GET /api/show
- POST /api/chat
- POST /api/generate

### Web UI (static, served by Ktor)

Browser-based control surface served directly by Ktor on the local LAN.
Provides complete interaction (chat, diagnostics, configuration) from any browser client and can be updated independently without rebuilding the APK.

### Minimal Android UI

Minimal, local control surface programmatically built in pure Kotlin (no XML, no Compose).
Confined to local device controls: model selection via native file picker, server daemon toggle, log console inspection, and self-contained test ping.

## Module Boundary Direction

The implementation maintains these strict boundaries:

| Area | Should Own | Should Not Own |
|---|---|---|
| Runtime provider | Runtime-specific loading, execution, and token generation | Android UI state or HTTP mapping |
| Model manager | Model lifecycle and memory safety validation | HTTP request parsing |
| Request queue | Serialized FIFO request queueing and backpressure (429) | View rendering or model execution details |
| Server layer | HTTP endpoints, SSE formatting, and compatibility mapping | Runtime implementation details |
| UI layer (Android) | Device controls, model picking, and manual test pings | Inference pipeline or server lifecycle |
| Web UI (LAN) | Browser-based chat and status surface | Device-specific Android controls |

## Phasing & Roadmap

- **Phases 0–4:** COMPLETED (Bootstrap, Garden analysis, reproducible CI build, minimal HTTP server, LiteRT-LM runtime integration).
- **Phase 5 (Stabilization & API Semantics):** Governed by the 8 operational sessions in [`fable5/roadmap-sessioni.md`](../fable5/roadmap-sessioni.md).
- **Phase 6 (Edge Extensions & Backlog):** Governed by [`fable5/backlog.md`](../fable5/backlog.md).

## Resolved Questions

The architectural questions from the bootstrap phase are resolved as follows:

- **Android project template:** Minimal Kotlin programmatic skeleton without Compose/XML for lightweight APK and build reproducibility (ADR 5, ADR 6).
- **Runtime engine:** LiteRT-LM (`litertlm-android`) selected as primary concrete provider (ADR 12).
- **Embedded HTTP server:** Ktor CIO chosen for asynchronous coroutine performance and low memory overhead (ADR 4).
- **Background execution & Doze:** Android Foreground Service (`specialUse` on API 34) with `PARTIAL_WAKE_LOCK` and high-performance `WifiLock` (ADR 2, ADR 11).
- **Request concurrency model:** Single-worker FIFO `RequestQueue` rejecting with HTTP 429 upon capacity overflow (ADR 13).

