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
  ENGINE  -> ModelManager  <----------  Ktor CIO routes (OpenAI + Ollama)
  DAEMON  -> start/stop service                 |
  TEST    -> HTTP calls to localhost            v
  LOGS    <- ServerConsole              RequestQueue (single worker, FIFO, 429)
                                                |
                                                v
                                   PromptBuilder -> InferenceProvider
                                                    (LiteRT-LM | Mock)
```

### InferenceProvider (`model/InferenceProvider.kt`)

Abstracts the runtime. Implementations: `LiteRtLmInferenceProvider` (`.litertlm`, CPU or
GPU backend) and `MockInferenceProvider`.

Current limits (addressed in roadmap/backlog):
- Receives an already templated `prompt: String` (Gemma template from `PromptBuilder`).
  Possible double templating with LiteRT-LM `Conversation` — to verify in M2. A second
  engine would require passing structured `messages` instead.
- `maxTokens` is accepted but not applied (M2).
- A new `Conversation` per request: no KV/prefix reuse (M5).

Other engines (GGUF/llama.cpp, ONNX, ExecuTorch) are backlog, only on concrete need.

### ModelManager (`model/ModelManager.kt`)

Model lifecycle: selection via system file picker, load/unload, active model, CPU/GPU
choice, hardware profile (SoC, OpenCL), RAM feasibility audit, cache purge of non-model
files in the dedicated models directory.

### RequestQueue (`model/RequestQueue.kt`)

Single-worker FIFO, depth 4, timeout 120 s, HTTP 429 with `Retry-After` on overflow or
timeout. Streaming holds the worker until completion. Unit-tested.

### Server layer (`service/LlmServerService.kt`)

Ktor CIO inside a Foreground Service (`specialUse`), `PARTIAL_WAKE_LOCK`, high-performance
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
