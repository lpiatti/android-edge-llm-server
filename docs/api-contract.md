# API contract — definition of "complete"

Agent-facing specification of the HTTP layer. This document defines what "complete
OpenAI and Ollama compatibility" means for this project. Milestones in
[roadmap.md](roadmap.md) reference its sections. Where code differs, this document wins;
deliberate deviations are recorded in [`DECISIONI.md`](../DECISIONI.md).

**Acceptance principle:** compatibility is proven by real clients (official `openai`
Python SDK, Open WebUI, one agent harness), not only by our own in-app tester — client and
server written by the same hands can agree on the same mistake.

## 0. Compatibility matrix

Status legend: ✅ done · 🟡 partial · ❌ missing · ⛔ out of scope (see §9).
Status verified against code at tag `v1`.

### OpenAI

| Endpoint / field | Status | Milestone | Notes |
|---|---|---|---|
| `GET /v1/models` | ✅ | — | Returns the active model. |
| `POST /v1/chat/completions` — `messages` full history | ✅ | — | `PromptBuilder` (Gemma template). |
| — `stream` (SSE, `[DONE]`) | ✅ | — | |
| — `temperature`, `top_p` | ✅ | — | Via `SamplerConfig`. |
| — `max_tokens` / `max_completion_tokens` | ❌ | M2 | `max_tokens` is parsed but silently ignored by `LiteRtLmInferenceProvider`; `max_completion_tokens` not parsed. Same for Ollama `num_predict`. |
| — `content` as array of parts (`[{type:"text",text}]`) | ❌ | M2 | Declared as `String`: modern clients fail deserialization. |
| — `stop` | ❌ | M2 | |
| — `usage` (prompt/completion tokens) | 🟡 | M2 | Must be engine counts or a consistent estimate. |
| — `stream_options.include_usage` | ❌ | M2 | Final usage chunk. |
| — `response_format: {type:"json_object"}` | ❌ | M2 | Best effort via prompt; document the limit. |
| — `tools`, `tool_choice`, `tool_calls`, `role:"tool"` | ❌ | M4 | See §3. Today `tools` is silently dropped (`ignoreUnknownKeys`). |
| — `n > 1`, `logprobs`, `seed` | ⛔ | — | Reject `n>1` with 400; ignore others with a log warning. |
| Error format (§7) | 🟡 | M2 | Verify every error path. |
| Auth (§5) | ❌ | M6 | |

### Ollama

| Endpoint / field | Status | Milestone | Notes |
|---|---|---|---|
| `GET /api/tags` | 🟡 | M3 | Lists the active model, but `size`, `digest`, `modified_at` are mock constants. |
| `POST /api/chat` full history + NDJSON stream | ✅ | — | |
| — `options.temperature`, `top_p` | ✅ | — | `num_predict`: see `max_tokens` row above. |
| — `options.stop`, `num_ctx` | ❌ | M3 | `num_ctx` accepted and logged if engine can't honor it. |
| — `tools` / `message.tool_calls` | ❌ | M4 | |
| `GET /api/version` | ❌ | M3 | Open WebUI probes it to detect Ollama. |
| `POST /api/show` | ❌ | M3 | Minimal valid metadata (family, parameter size, template, capabilities). |
| `POST /api/generate` | ❌ | M3 | Prompt-style, stream + non-stream. |
| `GET /api/ps` | ❌ | M3 | Loaded model + size. |
| `pull`, `push`, `create`, `copy`, `delete` | ⛔ | — | Return 501 with a clear message. |

### Service endpoints

| Endpoint | Status | Notes |
|---|---|---|
| `GET /health` | ✅ | Always unauthenticated. |

## 1. Statelessness (fundamental)

- The CLIENT owns conversation history and sends the FULL `messages` array every request.
- The SERVER processes the ENTIRE array (system, user, assistant, tool turns).
- No server-side sessions. Consequence: every request re-prefills the whole context.
  This cost is measured (M1) and reduced transparently by prefix/KV reuse (M5) — an
  optimization that must never change the stateless semantics.

## 2. POST /v1/chat/completions

| Field | Behavior |
|---|---|
| `messages` | Full array in order. Roles: `system`, `user`, `assistant`, `tool`. `content` may be a string or an array of text parts (concatenate text parts; non-text parts → 400 until multimodal is in scope). |
| `model` | Informational; respond with the ACTIVE model name (honest). |
| `stream` | SSE per OpenAI spec. First delta has `role:"assistant"`; last content chunk has `finish_reason`; terminate with `data: [DONE]`. Mid-stream error → final chunk with `error`, then `[DONE]`. |
| `temperature`, `top_p`, `max_tokens`, `max_completion_tokens`, `stop` | Pass to engine if supported; otherwise enforce server-side (`stop`, token cap) or log a warning. Never pretend silently. |
| `tools`, `tool_choice` | §3. |

Prompt assembly: apply the active model's chat template exactly once. **Open question
(M2):** the provider passes an already Gemma-templated string to LiteRT-LM
`Conversation.sendMessageAsync`, which may apply its own template again. Verify and fix
(feed native messages if the SDK supports it).

## 3. Tool calling

**API layer (what the client sees):**
- Request: `tools: [{type:"function", function:{name, description, parameters}}]`, `tool_choice`.
- Response when the model calls a tool: `message.tool_calls: [{id, type:"function", function:{name, arguments:"<json string>"}}]`, `finish_reason:"tool_calls"`. Ollama: `message.tool_calls` with `arguments` as an object.
- Follow-up request carries `{role:"assistant", tool_calls:[…]}` and `{role:"tool", tool_call_id, content}`; both must reach the prompt.
- The server NEVER executes tools (phone-side tools are backlog, see [backlog.md](backlog.md)).

**Model layer:**
- Preferred: LiteRT-LM native function-calling API for Gemma 4, if exposed (verify first in M4).
- Fallback: inject tool definitions using the model's documented format, parse the output;
  well-formed call → `tool_calls`, otherwise plain content.
- Parser rules: malformed JSON → content (never 500), multiple calls supported, `id` = `call_<uuid>`.
- Streaming: emit `tool_calls` deltas per OpenAI spec (M4, second step).

## 4. Concurrency

- Exactly ONE inference at a time. FIFO queue, depth 4, timeout 120 s (`RequestQueue`, done).
- Queue full or timeout → HTTP 429, OpenAI-style body, `Retry-After`.
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

OpenAI-style everywhere, Ollama endpoints included:
`{"error": {"message": "...", "type": "invalid_request_error" | "server_error" | "rate_limit_exceeded" | "authentication_error", "code": null}}`.
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
