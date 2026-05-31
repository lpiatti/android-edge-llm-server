# Documentation Index

This directory contains the persistent technical documentation for the Android Edge LLM Server project.

The repository is the source of truth for project state, roadmap, architectural direction, and agent workflow rules.

## Core Documents

| Document | Purpose | Status |
|---|---|---|
| README.md | Project overview, vision, initial direction, and working model | Active |
| docs/project-state.md | Current objective, constraints, operational workflow, and immediate priorities | Active |
| docs/roadmap.md | Phase-based roadmap from bootstrap to runtime integration | Active |
| docs/architecture.md | Initial conceptual architecture and boundaries | Active |
| docs/daemon-stability-guidelines.md | Platform SDK cut-offs, dedicated server profile, and background stability strategies | Active |
| docs/decision-log.md | Architecture decision registry from project bootstrap to present | Active |
| [docs/garden-analysis.md](file:///c:/python_sources/android-edge-llm-server/docs/garden-analysis.md) | Architectural analysis of Google AI Edge/Garden components, component maps, and integration roadmap | Active |

## Agent Instructions

Agent-oriented instructions are intentionally kept outside the docs directory.

| Path | Purpose |
|---|---|
| AGENTS.md | General operating contract for all coding agents |
| .agents/ | Agent-specific prompts and task briefs |
| CLAUDE.md | Claude Code entrypoint instructions |
| .claude/ | Claude Code project support area |

## Documentation Rules

- Keep project status in docs/project-state.md.
- Keep roadmap and phase changes in docs/roadmap.md.
- Keep architectural rationale in docs/architecture.md.
- Keep agent behavior, execution rules, and implementation guardrails in AGENTS.md and .agents/.
- Do not use chat history as source of truth when repository documentation conflicts with memory.

## Current Reading Order

For project orientation, read in this order:

1. README.md
2. docs/project-state.md
3. docs/roadmap.md
4. docs/architecture.md
5. docs/daemon-stability-guidelines.md
6. docs/decision-log.md
7. AGENTS.md
8. .agents/README.md
9. agent-specific files in .agents/
