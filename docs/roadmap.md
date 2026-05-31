# Roadmap

## Phase 0 — Repository Bootstrap

Status: COMPLETED

Goals:
- initialize repository
- define architecture direction
- establish operational workflow
- prepare documentation structure

Deliverables:
- README
- roadmap
- architecture notes
- project-state
- GitHub Actions bootstrap

---

## Phase 1 — Garden Analysis

Status: COMPLETED

Goals:
- understand Google Garden architecture
- identify reusable runtime components
- understand model lifecycle
- understand token streaming
- identify UI/runtime coupling points

Deliverables:
- architecture analysis document
- runtime component map
- dependency map
- integration strategy notes

---

## Phase 2 — Minimal Android Build

Status: COMPLETED

Goals:
- compile reproducible APK
- validate Android toolchain
- validate GitHub Actions Android pipeline
- validate artifact generation

Deliverables:
- working CI pipeline
- downloadable APK artifact
- first successful cloud build

---

## Phase 3 — Minimal Server Skeleton

Status: COMPLETED

Goals:
- embedded HTTP server
- health endpoint
- fake OpenAI-compatible endpoint
- validate Android background lifecycle

Initial Endpoints:
- GET /health
- GET /v1/models
- POST /v1/chat/completions

Deliverables:
- fake response server
- local network accessibility
- API compatibility tests

---

## Phase 4 — Runtime Integration

Status: IN PROGRESS

Goals:
- integrate Garden runtime
- load model
- execute inference
- stream generated tokens

Deliverables:
- real token generation
- inference abstraction layer
- streaming responses

---

## Phase 5 — Server Stabilization

Status: TODO

Goals:
- background execution stability
- Android lifecycle robustness
- foreground service management
- memory management

Deliverables:
- stable server mode
- long-running inference sessions
- improved runtime resilience

---

## Long-Term Direction

Potential future directions:
- multiple runtime providers
- LAN discovery
- agent integration
- lightweight orchestration
- embeddings support
- tool calling
- multimodal support
- distributed edge nodes
