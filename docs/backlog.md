# Backlog

Everything valuable that is NOT in [roadmap.md](roadmap.md). Items here must not be
started opportunistically; promoting one requires a project-owner decision recorded in
[`DECISIONI.md`](../DECISIONI.md). Order is a value judgment, not a queue.

## 1. Embeddings endpoint — first candidate for promotion

`POST /v1/embeddings` (+ Ollama `/api/embed`) backed by `embeddinggemma-300m`
(litert-community). First building block of the multi-phone RAG plan. Separate
`InferenceProvider` instance, own queue lane (embedding requests are fast and shouldn't
wait behind a chat generation).

## 2. Additional inference engines — only on concrete need

`InferenceProvider` is the extension point. Rule: LiteRT-LM stays the primary engine; a
new engine is added only when a concrete need exists that LiteRT-LM cannot cover (e.g. an
indispensable model available only in another format). No speculative generalization.
Evaluation (2026-09-26) — see `DECISIONI.md`:
- **GGUF / llama.cpp**: largest model catalog, best ARM CPU path, GBNF grammars for
  constrained JSON. Cost: NDK/CMake/JNI build in CI, arm64-v8a only. Does NOT improve
  prefill vs LiteRT-LM GPU. First candidate if a need arises.
- **ONNX Runtime (GenAI)**: interesting for Qualcomm NPU (QNN) and for embeddings/rerankers;
  NPU not practically reachable on current test devices (Pixel 9 Tensor, S20 FE).
- **ExecuTorch**: alternative to ONNX for NPU targets.
- **MediaPipe tasks-genai**: likely obsolete (superseded by LiteRT-LM upstream) — verify
  before any work.
Prerequisite for any second engine: pass structured `messages` to the provider instead of
a pre-templated prompt string, so each engine applies its own chat template.

## 3. Web UI served by Ktor (was session S7)

Static web UI at `/ui` from `getExternalFilesDir(null)/webui/`, updatable from a GitHub
Release without rebuilding the APK; `/admin/status`, `/admin/model/load|unload`,
`/admin/ui/update`. All `/admin/*` require the API key (M6); refuse `/admin/*` when no key
is set and the bind host is not localhost.

## 4. Model Hub (download models in-app)

Curated list from a remote JSON (configurable URL, no hardcoded links), downloads from
HuggingFace into `getExternalFilesDir(null)/models/`, resume + SHA256. Download/ folder
stays as manual fallback. Shares downloader code with the Web UI updater.

## 5. Multi-node federation ("no central point")

1. **Stage 0 (free, already true)**: each phone is an independent stateless server; an
   external orchestrator picks the node.
2. **Stage 1**: static peer list; `GET /v1/models` aggregates peers; reverse proxy.
3. **Stage 2 (only if needed)**: mDNS/NSD discovery, health checks.
No consensus protocols, ever, at this scale.

## 6. Tools executed on the phone

Expose phone capabilities (battery, sensors, notifications, TTS, camera) as tools the
model can call on the server side. This is the OPPOSITE of the API contract (§3: the
server never executes tools), so it is a different product mode: API key mandatory,
per-tool opt-in in the UI, read-only tools first. Consider aligning with MCP. Note: the
M4 in-app self-test already runs a read-only phone tool, but client-side inside the app,
not exposed to the network.

## 7. RAG agentic orchestrator — explicitly OUTSIDE the app

Parsing, embeddings, vector search, reranking, ReAct loop across 2–3 phones. Separate
client consuming the phones' APIs. Never inside the Android app.

## 8. Smaller items

- **Multimodal input** (image/audio parts in `messages`) — Gemma 4 supports it; needs
  engine support check.
- **TTS endpoint** (`/v1/audio/speech`) via native Android TTS.
- **TinySD image generation** — parked; RAM-hungry.
- **README polish**: demo GIF, quickstart, release badge, GitHub Release with APK.
- **CI on every push to feature branches** (see [ci.md](ci.md)).
