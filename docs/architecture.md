# Architecture

Architecture as it is at tag `v1`, plus the direction set by [roadmap.md](roadmap.md).

## Purpose

Android-native edge server exposing local LLM inference through OpenAI- and
Ollama-compatible HTTP APIs. It stays server-oriented: it must not evolve into a
chat-centric Android app.

## Principles

1. Inference runtime independent from UI and from the HTTP layer.
2. API compatibility independent from the runtime provider.
3. Incremental changes over rewrites.
4. Build reproducibility and CI visibility (no local builds, see `AGENTS.md`).
5. Every architectural shift recorded in `DECISIONI.md`.

## Components

```text
MainActivity (UI, 4 tabs)            LlmServerService (Foreground Service)
  ENGINE  -> ModelManager  <----------  api/ApiServer (Ktor routes, OpenAI + Ollama)
  DAEMON  -> start/stop, API key                 |  api/OpenAiApi, api/OllamaApi (DTO + mapping)
  TEST    -> HTTP calls to localhost            v
  LOGS    <- ServerConsole              RequestQueue (single worker, FIFO, 429)
                                                |
                                                v
                     GenerationRequest -> InferenceProvider.generate() -> Flow<GenerationEvent>
                                          (LiteRtLmInferenceProvider | MockInferenceProvider)
```

### Inference layer (`model/`)

- `ChatTypes.kt`: engine-agnostic request/event types (`ChatTurn`, `ToolDefinition`,
  `GenerationRequest`, `GenerationEvent`).
- `ConversationPlanner.kt`: splits a stateless request into system instruction, history and
  input; decides when the cached conversation can be reused.
- `GenerationPipeline.kt`: server-side `stop` sequences and result aggregation.
- `LiteRtLmInferenceProvider.kt`: LiteRT-LM SDK mapping — native history, native tool calling
  (`automaticToolCalling = false`), `maxOutputToken`, constrained JSON, KV reuse, cancellation.
- `InferenceProvider.kt`: the interface and the mock provider (also simulates tool calls).

All of it except the LiteRT-LM provider is pure Kotlin and covered by JVM tests.

### ModelManager (`model/ModelManager.kt`)

Model lifecycle: selection via system file picker, load/unload, active model, CPU/GPU
choice, context size, hardware profile (SoC, OpenCL), RAM feasibility audit (pre-flight
refusal with "force"), crash marker for loads killed by the OS, cache purge of non-model
files in the dedicated models directory.

### RequestQueue (`model/RequestQueue.kt`)

Single-worker FIFO, depth 4, timeout 120 s, HTTP 429 with `Retry-After` on overflow or
timeout. Streaming holds the worker until completion. Unit-tested.

### Server layer (`service/LlmServerService.kt`, `api/`)

`LlmServerService` hosts Ktor CIO inside a Foreground Service (`specialUse`), `PARTIAL_WAKE_LOCK`, high-performance
`WifiLock`, `START_STICKY`, `BootReceiver`. Bind host selectable (all / Wi-Fi / cellular).
Endpoints and their compatibility status: [api-contract.md](api-contract.md) §0.
Stability rules: [daemon-stability-guidelines.md](daemon-stability-guidelines.md).

### Android UI (`ui/MainActivity.kt`, `ui/UiComponents.kt`)

Programmatic Kotlin, no XML, no Compose, TUI/terminal style. Flow: crash report (if any)
→ permission onboarding → main view with 4 tabs:
- **ENGINE**: model file, CPU/GPU, load/unload, hardware and RAM audit.
- **DAEMON**: HTTP server start/stop, bind interface, live telemetry.
- **TEST**: quick shell, API presets, raw HTTP console (calls the server over HTTP).
- **LOGS**: centralized console.

The ENGINE/DAEMON separation mirrors the engine/server separation in code and is kept.
M7 reorganizes the TEST tab and splits `MainActivity.kt` (~3000 lines) per tab.

## Module boundaries

| Area | Owns | Must not own |
|---|---|---|
| Runtime provider | Engine loading, execution, token generation | UI state, HTTP mapping |
| ModelManager | Model lifecycle, memory checks | HTTP parsing |
| RequestQueue | Serialization and backpressure | Rendering, engine details |
| Server layer | Endpoints, SSE/NDJSON, compatibility mapping, prompt assembly call | Engine internals |
| Android UI | Device controls, model picking, tests via HTTP | Inference pipeline, server lifecycle |

Closing or swiping away the UI must not stop the server nor release locks.
