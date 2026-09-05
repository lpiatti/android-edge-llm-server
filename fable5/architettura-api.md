# Target API contract

Agent-facing specification for the HTTP layer. This document defines the behavior the
server MUST implement; sessions in [roadmap-sessioni.md](roadmap-sessioni.md) reference
these sections. Where current code differs, this document wins (record deviations in
`docs/decision-log.md`).

## 1. Statelessness (fundamental)

The server is stateless between requests, exactly like the OpenAI API:
- The CLIENT owns conversation history and sends the FULL `messages` array every request.
- The SERVER must process the ENTIRE array — system prompt, all user/assistant/tool
  turns — and build the model context from it. Using only the last user message is a
  contract violation (this was the pre-S1 bug).
- No server-side sessions, no server-side memory. Consequence: every request re-processes
  (prefills) the whole context on-device. This cost is accepted and measured (S1
  benchmark); KV-cache reuse is a backlog optimization, not part of the contract.

## 2. POST /v1/chat/completions (OpenAI)

Request fields the server MUST honor:

| Field | Behavior |
|---|---|
| `messages` | Full array processed in order. Roles: `system`, `user`, `assistant`, `tool`. |
| `model` | Informational; single active model responds regardless. Echo it back? No — respond with the ACTIVE model name (honest). |
| `stream` | SSE per OpenAI spec when true. |
| `temperature`, `top_p`, `max_tokens` | Pass to engine if supported; otherwise log warning, never silently pretend. |
| `tools`, `tool_choice` | See §3. |

Prompt assembly (when the engine has no native message API): apply the active model's
chat template (Gemma template for Gemma models) — never invent a generic
"System: ...\nUser: ..." format, templates matter for small models.

Response: standard OpenAI schema (already implemented). `usage` token counts: use engine
counts if available, else estimate and keep the estimate consistent.

SSE stream details: first chunk delta includes `role:"assistant"`; final content chunk
has `finish_reason:"stop"`; terminate with `data: [DONE]`. On mid-stream error, send a
final chunk with an `error` object, then `[DONE]`, then close.

## 3. Tool calling (OpenAI format)

Two layers — do not confuse them:

**API layer (what the client sees)** — this IS how the real OpenAI/Ollama APIs behave:
- Request: `tools: [{type:"function", function:{name, description, parameters}}]`.
- When the model decides to call a tool, the response message contains
  `tool_calls:[{id, type:"function", function:{name, arguments:"<json string>"}}]`
  and `finish_reason:"tool_calls"` (Ollama: `message.tool_calls`).
- The client executes the tool and sends a follow-up request appending
  `{role:"tool", tool_call_id, content:"<result>"}` to the history.
- The server NEVER executes tools.

**Model layer (how the server produces that)**:
- Preferred: the engine's native function-calling API if LiteRT-LM exposes it (verify
  in S5).
- Fallback: inject tool definitions into the prompt using the model's documented
  function-calling format, then PARSE the model's output; if it contains a well-formed
  call, translate it into the structured `tool_calls` field; otherwise return it as
  normal content. This translation job is exactly what Ollama does for most models.
- Parser rules: tolerate malformed JSON (return as content, never 500), support
  multiple calls, generate `id` as `call_<uuid>`.

## 4. Concurrency

- Exactly ONE inference runs at a time (single-model, on-device).
- Additional requests wait in a FIFO queue: max depth 4, queue timeout 120 s (constants,
  configurable later).
- Queue full or timeout → HTTP 429, OpenAI-style error body, `Retry-After: <seconds>`.
- Streaming requests hold the worker until the stream completes.

## 5. Authentication

- Optional single API key (set in Android UI, stored in SharedPreferences).
- When set: all endpoints except `GET /health` require `Authorization: Bearer <key>`;
  failures → 401 with OpenAI-style error body.
- When unset: open access (home-LAN profile), and the UI must say so explicitly.
- No TLS on-device (accepted for LAN; documented limitation).

## 6. Ollama surface (minimum for real clients)

| Endpoint | Notes |
|---|---|
| `GET /api/version` | Required — Open WebUI probes it to detect Ollama. `{"version":"<app version>"}` |
| `GET /api/tags` | Exists; keep honest values (real file size, not mock constants). |
| `POST /api/chat` | Full-history processing as §2; NDJSON streaming. |
| `POST /api/generate` | Prompt-style completion, stream + non-stream. |
| `POST /api/show` | Minimal valid metadata for the active model. |

## 7. Error format

OpenAI-style everywhere:
`{"error": {"message": "...", "type": "invalid_request_error" | "server_error" | "rate_limit_exceeded", "code": null}}`
Never leak stack traces to HTTP responses; they go to ServerConsole/crash log.

## 8. Web UI and admin endpoints (S7)

- `GET /ui` → static files from `getExternalFilesDir(null)/webui/` if present, else
  embedded fallback page.
- `GET /admin/status` → model, backend, uptime, stats (JSON).
- `POST /admin/model/load` `{path|name, backend}` / `POST /admin/model/unload`.
- `POST /admin/ui/update` → download+unzip webui.zip from configured release URL.
- All `/admin/*` require the API key when one is set; refuse `/admin/*` entirely if no
  key is set AND the bind host is not localhost — protect remote control by default.
