# Declared backlog (Phase N)

Everything valuable that does NOT fit the 8-session budget in
[roadmap-sessioni.md](roadmap-sessioni.md). Items here must not be started opportunistically
mid-session ("scope creep"); promoting one requires a project-owner decision and a
decision-log entry. Order below is a value judgment, not a queue.

## 1. Embeddings endpoint — first candidate for promotion

`POST /v1/embeddings` backed by `embeddinggemma-300m` (available on litert-community).
First building block of the multi-phone RAG plan. Small model, low RAM, can run on the
"weaker" phone. Design: separate `InferenceProvider` instance, own queue lane (embedding
requests are fast and shouldn't wait behind a chat generation).

## 2. Multi-engine providers (nothing is lost a priori)

`InferenceProvider` is already the extension point — this was the reason it exists.
Candidates, in order of practical value:
- **ONNX Runtime Mobile**: opens embeddings/rerankers/parsers beyond .litertlm
  (MiniLM, bge-reranker, etc.). NNAPI/XNNPACK acceleration.
- **MediaPipe tasks-genai**: `.task` model catalog (Phi, Falcon, older Gemma).
Rule from ADR-12: LiteRT-LM stays the primary LLM engine; new engines arrive as new
providers behind the same interface, never as rewrites.

## 3. Model Hub (download models in-app, goodbye Download-folder juggling)

In-app curated list + downloader: metadata from a remote JSON (configurable URL,
default pointing to this repo — no hardcoded links in code per AGENTS.md), downloads
from HuggingFace into `getExternalFilesDir(null)/models/` (no permissions needed),
resume support + SHA256 verification. Removes the SAF/Download-folder friction entirely;
Download/ remains a manual fallback. Same mechanism serves web UI updates (S7) — share
the downloader code.

## 4. Multi-node federation ("no central point")

Anti-NASA staging — each stage is useful alone:
1. **Stage 0 (free, already true after S1–S4)**: every phone is an independent
   stateless server; the RAG orchestrator (outside the app) picks the right node per
   task. This covers the 2–3 phone scenario with zero new code.
2. **Stage 1**: static peer list in each node; `GET /v1/models` aggregates peers'
   models; requests for a remote model are reverse-proxied. ~1 session of work.
3. **Stage 2 (only if Stage 1 proves insufficient)**: mDNS/NSD discovery, health checks.
No consensus protocols, no leader election — ever, at this scale.

## 5. Tools on the phone (project owner flagged as interesting)

Expose phone capabilities as server-side tools the model can call: TTS speaker, camera
snapshot, sensors, notifications. Requires S5/S6 done first, plus a security story
(these DO execute on the server — opposite of the §3 contract — so API key mandatory
and per-tool opt-in in UI). Could later align with MCP instead of inventing a scheme.

## 6. RAG agentic orchestrator — explicitly OUTSIDE the app

The "project phase 2" Luigi described: parsing, embeddings, vector search, reranking,
ReAct loop across 2–3 phones. Lives as a separate client (laptop script or one phone
running it in Termux/web), consuming the phones' APIs. Vector store: SQLite or plain
files at this corpus scale. Do not put any of this inside the Android app.

## 7. Smaller items

- **MainActivity slimming**: after S7 proves the web UI, progressively move complex
  screens out of the 2,000-line activity. Android UI keeps: model load, logs, test call.
- **TTS endpoint** (`/v1/audio/speech`) via native Android TTS — cheap and fun demo.
- **KV-cache / prefix reuse** if LiteRT-LM ever exposes it — would slash agentic
  prefill cost; watch upstream releases.
- **TinySD image generation** — keep parked; RAM-hungry and off the critical path.
- **README polish for stars**: demo GIF, quickstart, release badge (part of S8 anyway).
