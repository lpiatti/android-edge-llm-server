# API contract — definition of "complete"

Agent-facing specification of the HTTP layer. This document defines what "complete
OpenAI and Ollama compatibility" means for this project. Milestones in
[roadmap.md](roadmap.md) reference its sections. Where code differs, this document wins;
deliberate deviations are recorded in [`DECISIONI.md`](../DECISIONI.md).

**Acceptance principle:** compatibility is proven by real clients (official `openai`
Python SDK, Open WebUI, one agent harness), not only by our own in-app tester — client and
server written by the same hands can agree on the same mistake.

## 0. Compatibility matrix

Legend: ✅ verified on device · 🔬 implemented + JVM-tested, device check pending (TEST tab) ·
❌ missing · ⛔ out of scope (see §9). Status as of the `v2` work (PR #9).

### OpenAI

| Endpoint / field | Status | Notes |
|---|---|---|
| `GET /v1/models`, `GET /v1/models/{id}` | 🔬 | Active model id = file name without `.litertlm`. |
| `POST /v1/chat/completions` — full history | 🔬 | Native LiteRT-LM history (`initialMessages`), template applied once. |
| — `stream` (SSE, `[DONE]`) | 🔬 | First delta carries `role`; mid-stream error → `error` event, then `[DONE]`. |
| — `temperature`, `top_p`, `seed` | 🔬 | `SamplerConfig`; missing values default to top_k 64, top_p 0.95, temperature 1.0. |
| — `max_tokens` / `max_completion_tokens` | 🔬 | Native `maxOutputToken`; `finish_reason:"length"` when reached. |
| — `content` as array of text parts | 🔬 | Non-text parts → 400. |
| — `stop` (string or array) | 🔬 | Enforced server-side, streaming-safe; native decoding is cancelled. |
| — `usage` + `prompt_tokens_details.cached_tokens` | 🔬 | Engine token count; cached = tokens served by KV reuse (M5). |
| — `stream_options.include_usage` | 🔬 | Final chunk with empty `choices` and `usage`. |
| — `response_format` `json_object` / `json_schema` | 🔬 | Native constrained decoding + system hint; falls back to hint only if the model rejects it. |
| — `tools`, `tool_choice`, `tool_calls`, `role:"tool"` | 🔬 | Native LiteRT-LM tool calling, `automaticToolCalling=false`; stream and non-stream. |
| — `n > 1` | ⛔ | 400. `logprobs` ignored. |
| Error format (§7) | 🔬 | |
| Auth (§5) | 🔬 | |
| `/v1/embeddings`, `/v1/completions` | ⛔ | 501. |

### Ollama

| Endpoint / field | Status | Notes |
|---|---|---|
| `GET /`, `GET /api/version` | 🔬 | `"Ollama is running"`; version = app version. |
| `GET /api/tags`, `GET /api/ps` | 🔬 | Real size and file date; digest is an identity hash, not a content hash. |
| `POST /api/show` | 🔬 | Family/size guessed from the file name; capabilities `completion`, `tools`. |
| `POST /api/chat` — history, NDJSON stream (default `stream:true`) | 🔬 | Final line carries `done_reason`, counts and durations (ns). |
| — `options.temperature/top_p/top_k/num_predict/stop/seed`, `format` | 🔬 | `num_ctx` ignored: context size is set at load time (ENGINE tab, CTX). |
| — `tools` / `message.tool_calls` / `role:"tool"` (+`tool_name`) | 🔬 | Arguments as JSON objects. |
| `POST /api/generate` | 🔬 | `system` + `prompt`; empty prompt = load check. |
| `pull`, `push`, `create`, `copy`, `delete`, `embed(dings)` | ⛔ | 501. |

### Service endpoints

| Endpoint | Status | Notes |
|---|---|---|
| `GET /health` | 🔬 | Always unauthenticated; reports model and `auth_required`. |

## 1. Statelessness (fundamental)

- The CLIENT owns conversation history and sends the FULL `messages` array every request.
- The SERVER processes the ENTIRE array (system, user, assistant, tool turns).
- No server-side sessions. Consequence: every request re-prefills the whole context.
  This cost is measured (M1) and reduced transparently by prefix/KV reuse (M5) — an
  optimization that must never change the stateless semantics.
- Reuse rule (M5): the conversation of the last successful request stays alive. It is reused
  only if the new request has the same system prompt, tools, sampling and JSON mode, and its
  history equals the cached history (assistant text compared trimmed, tool arguments compared
  as JSON). Any difference, a cancellation or an error → a fresh conversation.

## 2. POST /v1/chat/completions

| Field | Behavior |
|---|---|
| `messages` | Full array in order. Roles: `system`, `user`, `assistant`, `tool`. `content` may be a string or an array of text parts (concatenate text parts; non-text parts → 400 until multimodal is in scope). |
| `model` | Informational; respond with the ACTIVE model name (honest). |
| `stream` | SSE per OpenAI spec. First delta has `role:"assistant"`; last content chunk has `finish_reason`; terminate with `data: [DONE]`. Mid-stream error → final chunk with `error`, then `[DONE]`. |
| `temperature`, `top_p`, `max_tokens`, `max_completion_tokens`, `stop` | Pass to engine if supported; otherwise enforce server-side (`stop`, token cap) or log a warning. Never pretend silently. |
| `tools`, `tool_choice` | §3. |

Prompt assembly: the model's own chat template, applied exactly once by LiteRT-LM from
native messages (system → `systemInstruction`, history → `initialMessages`, last user
message or trailing tool results → the sent message). The previous string-template
approach (`PromptBuilder`, removed) risked double templating.

## 3. Tool calling

**API layer (what the client sees):**
- Request: `tools: [{type:"function", function:{name, description, parameters}}]`, `tool_choice`.
- Response when the model calls a tool: `message.tool_calls: [{id, type:"function", function:{name, arguments:"<json string>"}}]`, `finish_reason:"tool_calls"`. Ollama: `message.tool_calls` with `arguments` as an object.
- Follow-up request carries `{role:"assistant", tool_calls:[…]}` and `{role:"tool", tool_call_id, content}`; both must reach the prompt.
- The server NEVER executes tools (phone-side tools are backlog, see [backlog.md](backlog.md)).

**Model layer:**
- LiteRT-LM 0.16.1 exposes native tool calling: tools are declared as `OpenApiTool`s in
  `ConversationConfig` with `automaticToolCalling = false`, so the SDK returns the model's
  calls instead of executing them. Tool results go back as `Content.ToolResponse`.
- Multiple calls supported; `id` = `call_<random>`; OpenAI `tool_call_id` is resolved to the
  function name from the preceding assistant message.
- Streaming: each call is emitted as one `tool_calls` delta (complete arguments), then
  `finish_reason:"tool_calls"`.

## 4. Concurrency

- Exactly ONE inference at a time. FIFO queue, depth 4, timeout 120 s (`RequestQueue`, done).
- Queue full or timeout → HTTP 429 with `Retry-After`.
- Streaming requests hold the worker until the stream completes.

## 5. Authentication

- Optional single API key (set in Android UI, stored in SharedPreferences).
- When set: all endpoints except `GET /health` require `Authorization: Bearer <key>`; 401 otherwise.
- When unset: open access (home LAN profile) and the UI says so explicitly.
- No TLS on-device (documented limitation).

## 6. Performance targets (measured, not promised)

Recorded by the in-app benchmark (M1) per device and backend (CPU/GPU):
- TTFT at ~500 / ~2000 / ~4000 prompt tokens.
- Decode tokens/s.
- TTFT on a second turn sharing the prefix (M5 target: substantially lower than cold).

Pivot rule: if cold TTFT at ~4000 tokens exceeds ~30–40 s on the best device/backend,
agent-harness use is declared impractical and M4 is re-prioritized (decision in `DECISIONI.md`).

## 7. Error format

OpenAI endpoints: `{"error": {"message": "...", "type": "invalid_request_error" | "server_error" | "rate_limit_exceeded" | "authentication_error"}}`.
Ollama endpoints: Ollama's own `{"error": "..."}` (what Ollama clients parse).
Status codes: 400 invalid request, 401 auth, 429 queue, 501 unsupported, 503 no model, 500 engine failure.
Never leak stack traces to HTTP responses; they go to ServerConsole/crash log.

## 8. Test surface

Every milestone that changes the API adds:
- JVM unit tests for pure logic (parsers, templates, schemas).
- An in-app test in the TEST tab (benchmark, tool-call self-test, API presets).
- One external-client check listed in the milestone acceptance.

## 9. Out of scope (for "complete")

- `/v1/embeddings`, `/api/embed` (need a second loaded model) → backlog.
- Legacy `/v1/completions`.
- Image/audio input, even though Gemma 4 is multimodal → backlog.
- Ollama model management (`pull`, `create`, `delete`, …).
- Web UI and `/admin/*` endpoints → backlog.
