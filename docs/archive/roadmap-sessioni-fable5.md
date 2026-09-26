# Session Roadmap — Phase 5/6 execution plan

Operational plan for the next agent coding sessions. Language: English (agent-facing).
Each session = one feature branch = one PR = green CI + evidence. Budget: 8 sessions
(S0 is a light doc-only extra). Anything not listed here lives in [backlog.md](backlog.md).

## Common Definition of Done (applies to every session)

- GitHub Actions CI green on the PR (build + unit tests once they exist).
- New/changed behavior covered by JVM unit tests where the logic is pure (no Android deps).
- Evidence attached to the PR: curl transcripts and/or screenshots per the session's
  acceptance criteria. "It should work" is not evidence.
- `docs/project-state.md` updated if project status changed; new decisions recorded in
  `docs/decision-log.md`.
- No scope creep: features not listed in the session scope go to [backlog.md](backlog.md).
- Report using the format in `AGENTS.md`.

## Summary table

| # | Branch | Objective | Key acceptance criterion |
|---|--------|-----------|--------------------------|
| S0 | `docs/fable5-alignment` | Apply [correzioni-doc.md](correzioni-doc.md) | Docs consistent, links valid |
| S1 | `feature/api-full-context` | Process full `messages` array + params passthrough | Multi-turn curl: model recalls earlier turns; prefill benchmark recorded |
| S2 | `feature/request-queue` | Serialize inference, FIFO queue, 429 | Parallel requests: no crash, correct queueing |
| S3 | `feature/safe-model-loading` | RAM pre-flight, crash marker, honest errors | Oversized model → clear refusal, not silent crash |
| S4 | `feature/ollama-complete-auth` | `/api/version`, `/api/generate`, `/api/show`, optional API key | Open WebUI connects and streams |
| S5 | `feature/tool-calling-1` | Tools in request, prompt template, output parser | Non-stream tool_calls round-trip via curl |
| S6 | `feature/tool-calling-2` | Streaming tool calls + real harness validation | An agent harness completes a task using one tool |
| S7 | `feature/web-ui-v1` | Static web UI served by Ktor, updatable without APK rebuild | Browser chat + status; UI updated from GitHub release |
| S8 | `feature/consolidation-release` | Bugfixes, doc sync, tagged release | GitHub Release with APK artifact |

---

## S0 — Documentation alignment (light, no code)

**Objective**: apply every instruction in [correzioni-doc.md](correzioni-doc.md).
**Scope IN**: markdown files only. **Scope OUT**: any code change.
**Acceptance**: all items in correzioni-doc.md checked off; every relative link in
`docs/` and `fable5/` resolves; CI bootstrap-verification job green.

## S1 — Correct API semantics (full context)

**Objective**: the server must use the ENTIRE `messages` array it receives, per the
contract in [architettura-api.md](architettura-api.md) §1–2, and pass sampling
parameters through to the engine.

**Scope IN**:
- Prompt assembly from full history (system + user + assistant turns) for both
  `/v1/chat/completions` and `/api/chat`, using Gemma's chat template.
- Pass `temperature`, `top_p`, `max_tokens` to LiteRT-LM where the SDK supports them;
  log a warning for unsupported ones. Verify what `EngineConfig`/session API actually
  accepts — do not guess.
- JVM unit tests for prompt assembly (pure Kotlin, no Android imports).
- Prefill benchmark: measure time-to-first-token with ~500 / ~2000 / ~4000-token
  contexts on a real device (manual step for Luigi, script/curl provided by the agent).
  Record numbers in the PR and in `docs/project-state.md`.

**Scope OUT**: tool calling, queueing, KV-cache reuse across requests.
**Likely files**: `LlmServerService.kt`, `InferenceProvider.kt`, new `PromptBuilder.kt`
(pure Kotlin, testable), new `app/src/test/.../PromptBuilderTest.kt`.
**Acceptance criteria**:
1. Unit tests green in CI (add `gradle testDebugUnitTest` to the workflow if missing).
2. curl transcript in PR: 3-turn conversation where turn 3 asks "what did I say first?"
   and the model answers correctly.
3. Benchmark table (device, backend, context size, TTFT) posted in PR.
**Risk**: LiteRT-LM Conversation API may force its own message structure; if so, feed
history via its native multi-message API instead of a flattened prompt — document which.

## S2 — Request queue

**Objective**: never run two inferences concurrently; queue or reject excess load.
**Scope IN**:
- FIFO queue (single worker) in front of the active provider; configurable max depth
  (default 4) and queue timeout (default 120 s).
- Excess requests → HTTP 429 with OpenAI-style error body and `Retry-After` header.
- Remove `System.gc()` calls in `onTrimMemory`/`onLowMemory` (keep the logging).
- Unit tests for queue logic (pure Kotlin coroutines, no Android).
**Scope OUT**: multi-model routing, federation, priorities.
**Likely files**: new `RequestQueue.kt` + test, `LlmServerService.kt`, `ModelManager.kt`.
**Acceptance**: two simultaneous curl streams → both complete sequentially, no crash;
5th request while 4 queued → 429. Transcript in PR.

## S3 — Safe model loading

**Objective**: loading a model that cannot fit must produce a clear message, never a
silent native crash.
**Scope IN**:
- Pre-flight check before engine init: model file size × safety factor (start with 1.4,
  make it a named constant) vs `ActivityManager.MemoryInfo.availMem`. On failure: refuse
  with message "Model needs ~X GB, ~Y GB available — try the E2B variant or CPU mode",
  plus an explicit "Force load anyway" override in the UI.
- Crash marker: write `filesDir/loading.marker` (model name + backend inside) before
  engine init, delete on success. On app start, if marker exists → show "Loading MODEL
  on BACKEND killed the app (out of memory). Suggestion: …" in the console, then delete.
- Device-aware hint text (GPU OpenCL variance across Adreno/Mali/Xclipse).
**Scope OUT**: actually making E4B fit (may be physically impossible on 8 GB devices).
**Likely files**: `ModelManager.kt`, `MainActivity.kt`, `LlmServerService.kt`.
**Acceptance**: on a device where E4B GPU crashes today (Galaxy S25 FE): load attempt →
either pre-flight refusal message, or (after Force) post-restart marker diagnosis.
Screenshot in PR. Unit test for the size/RAM decision function.

## S4 — Ollama completeness + optional auth

**Objective**: pass real-client detection (Open WebUI) and add opt-in API key.
**Scope IN**:
- `GET /api/version` (static version string from BuildConfig).
- `POST /api/generate` (prompt-style, stream + non-stream).
- `POST /api/show` (minimal valid model metadata).
- Optional API key: single key stored in SharedPreferences, set from UI; when set, all
  endpoints except `/health` require `Authorization: Bearer <key>`; 401 otherwise.
- Manual validation checklist with Open WebUI (connect as Ollama AND as OpenAI provider).
**Scope OUT**: multi-user auth, TLS (LAN profile accepted, documented).
**Likely files**: `LlmServerService.kt`, `MainActivity.kt` (key field), tests for schemas.
**Acceptance**: Open WebUI lists the model and streams a chat reply — screenshots in PR;
curl 401/200 transcript with and without key.

## S5 — Tool calling, part 1 (non-streaming)

**Objective**: OpenAI-format tool calling: tools declared by the client, calls returned
structured to the client. The client executes tools — never the server.
**Scope IN**:
- Accept `tools` (function declarations) and `tool_choice` in `/v1/chat/completions`;
  accept `role:"tool"` messages in history.
- FIRST: verify whether LiteRT-LM 0.11+ exposes Gemma 4's native function-calling API.
  If yes, use it. If no: inject tool definitions via prompt template and parse the
  model's JSON output (Ollama-style approach). Record the finding in decision-log.
- Non-stream response with `tool_calls` + `finish_reason:"tool_calls"` per
  [architettura-api.md](architettura-api.md) §3.
- Robust parser with unit tests (malformed JSON, no-call output, multiple calls).
**Scope OUT**: streaming tool calls, parallel calls guarantees, Ollama tools.
**Acceptance**: unit tests green; curl round-trip in PR: request with a `get_weather`
tool → response with structured tool_call → follow-up with `role:"tool"` result →
final answer using it.

## S6 — Tool calling, part 2 + harness validation

**Objective**: streaming tool calls and proof with a real agent harness.
**Scope IN**:
- Streaming: emit `tool_calls` deltas in SSE chunks per OpenAI spec.
- Multi-turn tool loop hardening (several call/result cycles in one conversation).
- Validation: connect one real harness (pick the simplest that works against a custom
  OpenAI base URL) and complete a task using at least one tool.
**Scope OUT**: MCP, tool execution on the phone (backlog).
**Acceptance**: harness session transcript in PR (task completed with ≥1 tool call);
decision on GO/NO-GO for the agentic use case recorded in `docs/decision-log.md` —
this is the second pivot point from [verdetto.md](verdetto.md).

## S7 — Web UI v1

**Objective**: rich control surface in the browser, updatable WITHOUT rebuilding the APK.
The Android UI stays and keeps: model load, logs, one test call (per project owner).
**Scope IN**:
- Ktor static serving: if `getExternalFilesDir(null)/webui/` contains `index.html`,
  serve it at `/ui`; else serve a minimal embedded fallback page from assets.
- "Update Web UI" button (Android UI + `/admin/ui/update`): download a `webui.zip` from
  a configurable GitHub Release URL (default = this repo's releases, stored in
  SharedPreferences, NOT hardcoded per AGENTS.md rule), unzip into the webui dir.
- Web UI content v1 (plain HTML/JS/CSS, no framework, no build step): status panel
  (model, uptime, stats), chat test page using the existing streaming API, model
  load/unload via new `/admin/model` endpoints. All `/admin/*` require the API key
  from S4 when set.
**Scope OUT**: shrinking MainActivity (backlog), user management, HTTPS.
**Acceptance**: from a LAN browser: see status, load model, run a streamed chat.
Replace webui folder contents via the update button → new UI visible after refresh,
same APK. Screenshots in PR.

## S8 — Consolidation and release

**Objective**: stabilize, sync docs, ship.
**Scope IN**: bugfixes that emerged in S1–S7; full doc sync (project-state, roadmap,
decision-log, README known-limitations section); version bump; git tag `v0.2.0`;
GitHub Release with the CI-built APK attached; README badge + short demo section.
**Scope OUT**: any new feature.
**Acceptance**: CI green on main after merge; Release published with APK; docs describe
reality (spot-check: every claim in README verifiable against code).
