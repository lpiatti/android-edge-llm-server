# Documentation alignment instructions (Session S0)

Instructions for a coding agent to bring the existing documentation in line with
reality and with the fable5 plan. Markdown only — NO code changes in this session.

**Already done directly during the consultancy (do NOT redo, just verify presence):**
- `LICENSE` replaced with PolyForm Noncommercial 1.0.0.
- `README.md` Licensing section updated.
- `docs/decision-log.md`: ADR 12 (multi-provider / LiteRT-LM first), ADR 13
  (SessionManager superseded), ADR 14 (license change) appended.
- `docs/project-state.md`: Immediate Priorities section rewritten to point to
  `fable5/roadmap-sessioni.md`.

## Tasks

### 1. docs/architecture.md
- Replace the `SessionManager` box in the conceptual diagram and its component section
  with `RequestQueue` (serialize inference, FIFO, reject with 429 — see ADR 13 and
  `fable5/architettura-api.md` §4).
- Add a `Web UI (static, served by Ktor)` element to the architecture: browser-based
  control surface, updatable without APK rebuild; Android UI remains a minimal local
  control (model load, logs, test call).
- In the InferenceProvider section, state explicitly: LiteRT-LM is the first concrete
  provider; ONNX Runtime and MediaPipe are backlog candidates behind the same interface
  (ADR 12).
- Remove or answer the stale "Open Questions" at the bottom (most are answered: Ktor
  CIO chosen, FGS strategy decided, etc.). Answered ones move into a short "Resolved"
  list with links to decision-log entries.

### 2. README.md
- Add a short **Known limitations (current phase)** section, honest and dated:
  full-history processing, tool calling, request queueing and web UI are IN PROGRESS
  per `fable5/roadmap-sessioni.md`; single concurrent inference; no TLS (LAN profile).
- Add a **Project direction** line linking to `fable5/index.md`.
- In "Core Features", tone down "OpenAI & Ollama Compatibility" to "OpenAI & Ollama
  compatible endpoints (see Known limitations)" until S4/S6 close the gap.
- Keep the Tested Models section; the `google/gemma-4-E2B-it` link is valid (verified
  2026-07-05).

### 3. docs/roadmap.md
- Under Phase 5, replace the generic goals list with: "Operational plan:
  `fable5/roadmap-sessioni.md` (S1–S8)". Keep Phase 6 as is, but move items now covered
  by fable5 backlog to a single line referencing `fable5/backlog.md`.

### 4. docs/garden-analysis.md
- Add a banner at the top: "Historical note (2026-07-05): the recommendation below to
  start with MediaPipe tasks-genai was superseded by ADR 12 in docs/decision-log.md —
  LiteRT-LM was integrated first. The multi-provider abstraction advice remains valid."
  Do not edit the body (decisions are superseded, not erased).

### 5. AGENTS.md
- In "Mandatory Startup Protocol" required reading, insert `fable5/index.md` and
  `fable5/roadmap-sessioni.md` after `docs/index.md`.
- Replace the "Current Phase Guardrail" allowed-work list with: "Work on the session
  assigned from fable5/roadmap-sessioni.md. Anything else requires project-owner
  approval." Keep the must-not list, and add: "Do not start backlog items
  (fable5/backlog.md) opportunistically."

### 6. docs/index.md
- Add the `fable5/` directory to the documentation tables with a one-line description
  and update the reading order to include `fable5/index.md`.

### 7. .agents/README.md
- The files table lists only `bootstrap-phase-0.md` but the directory also contains
  `create-android-skeleton.md` — fix the table and mark both briefs as historical
  (completed phases). State that current task briefs are the sessions in
  `fable5/roadmap-sessioni.md`.

### 8. Link check (acceptance gate)
- Verify every relative link in `README.md`, `docs/*.md`, `fable5/*.md`,
  `.agents/README.md` resolves to an existing file. Fix broken ones. List the files
  checked in the PR description.

## Acceptance criteria for S0
- All 8 tasks done; CI bootstrap-verification job green; PR lists each touched file
  with a one-line rationale; no code files in the diff.
