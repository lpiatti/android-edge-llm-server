# Agent Working Rules

## Purpose

This document defines how AI agents should collaborate on this repository.

The goal is to avoid:
- architectural drift
- uncontrolled changes
- undocumented decisions
- repository chaos
- overengineering

## Human Roles

### Luigi

Acts as:
- product owner
- architectural decision maker
- orchestration lead

### ChatGPT

Acts as:
- strategic coordinator
- architecture reviewer
- roadmap maintainer
- continuity layer
- decomposition and reasoning layer

### Claude Code / Codex

Act as:
- operational implementation agents
- repository analyzers
- code generators
- refactoring assistants

## Repository Philosophy

The repository is the persistent source of truth.

Do not rely on:
- chat memory
- hidden state
- assumptions from previous sessions

Important decisions must be documented.

## Coding Philosophy

Prefer:
- small incremental changes
- isolated modules
- low coupling
- explicit naming
- minimal viable implementations
- maintainable abstractions

Avoid:
- massive rewrites
- speculative abstractions
- unnecessary frameworks
- hidden side effects
- tightly coupled UI/runtime logic

## Architectural Direction

The project is server-oriented.

The UI should remain minimal.

The inference runtime should be reusable independently from UI.

Target architecture:

InferenceProvider
    -> ModelManager
        -> SessionManager
            -> OpenAI Server
            -> Minimal Chat/Test UI

## Git Workflow

Initially keep workflow simple.

Preferred:
- small commits
- descriptive commit messages
- direct main branch updates during bootstrap phase

Avoid initially:
- complex branching models
- premature release engineering
- large rebases
- unnecessary process overhead

## CI/CD Philosophy

Primary objective initially:

push -> APK artifact

Not required initially:
- Play Store publishing
- advanced release automation
- complex testing matrix
- advanced deployment strategies

## Important Rule

When uncertain:
- preserve simplicity
- preserve modularity
- preserve maintainability

Do not optimize prematurely.
