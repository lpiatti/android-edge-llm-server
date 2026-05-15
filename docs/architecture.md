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
        -> SessionManager
            -> OpenAI-compatible Server
            -> Minimal Chat/Test UI
```

## Components

### InferenceProvider

Abstracts the underlying inference runtime.

Initial expected responsibility:

- expose a stable local interface for model loading and generation
- hide runtime-specific details
- allow future replacement or addition of providers

The project should initially study Google AI Edge / Garden components before committing to runtime integration details.

### ModelManager

Owns model lifecycle at application level.

Expected responsibilities:

- model discovery
- model metadata
- model loading and unloading
- active model tracking
- future model validation rules

### SessionManager

Owns inference sessions independently from Android UI screens.

Expected responsibilities:

- create and track sessions
- route requests to the active provider/model
- isolate request lifecycle from UI lifecycle
- prepare future support for streaming and concurrent requests

### OpenAI-compatible Server

Exposes local network endpoints compatible with OpenAI-style clients.

Initial target endpoints:

- GET /health
- GET /v1/models
- POST /v1/chat/completions

The first implementation may return fake responses to validate server lifecycle, API shape, and LAN access before runtime integration.

### Minimal Chat/Test UI

The UI is only a local validation and control surface.

It must not own inference logic.
It must not own API compatibility logic.
It must not be required by the server runtime path once the server is started.

## Module Boundary Direction

The future implementation should keep these boundaries clear:

| Area | Should Own | Should Not Own |
|---|---|---|
| Runtime provider | Runtime-specific loading and generation | Android UI state |
| Model manager | Model lifecycle | HTTP request parsing |
| Session manager | Request/session coordination | View rendering |
| Server layer | HTTP endpoints and compatibility mapping | Runtime implementation details |
| UI layer | Configuration and manual testing | Inference pipeline |

## Initial Phasing

### Phase 0

Repository, documentation, agent workflow, and CI bootstrap.

No real runtime integration.
No UI implementation.
No broad refactor.

### Phase 1

Study Google AI Edge / Garden architecture and document reusable components.

### Phase 2

Create a minimal reproducible Android build and CI artifact.

### Phase 3

Create a minimal server skeleton with fake OpenAI-compatible responses.

### Phase 4

Integrate real runtime progressively behind InferenceProvider.

## Explicit Non-Goals for the Bootstrap Stage

- No chat application architecture.
- No runtime fork strategy.
- No premature multi-provider abstraction beyond interface-level planning.
- No large agent-generated implementation batch.
- No hidden state kept only in chat.

## Open Questions

- Which Android project template will be used as the base?
- Which Garden components are reusable without excessive divergence?
- Which embedded HTTP server is most appropriate for Android constraints?
- How should long-running server mode interact with Android foreground services?
- Which API compatibility subset is required for the first useful client integration?
