# Android Edge LLM Server

Android-native edge AI server focused on exposing local LLM inference through OpenAI-compatible APIs.

## Vision

Turn Android devices into lightweight edge AI nodes capable of:

- running local LLM inference
- exposing OpenAI-compatible endpoints
- serving LAN/local clients
- integrating with existing AI tooling
- supporting future multi-runtime providers

The long-term direction is closer to an Android-native lightweight Ollama-style runtime than to a classic chat application.

## Initial Technical Direction

The project will initially study and reuse components from Google's AI Edge / Garden ecosystem:

- model lifecycle
- runtime loading
- inference pipeline
- token streaming
- device acceleration

The goal is to avoid maintaining low-level inference/runtime logic while focusing development on:

- API exposure
- orchestration
- lifecycle
- compatibility
- edge/server UX

## Immediate Milestones

1. Repository and workflow initialization
2. Android CI/CD pipeline with APK artifact generation
3. Garden repository architectural analysis
4. Minimal Android build reproducibility
5. Fake OpenAI-compatible server endpoints
6. Runtime integration
7. Streaming/token generation
8. Background/server mode stabilization

## Repository Structure

/docs
    architecture.md
    roadmap.md
    project-state.md
    agents/

/.github/workflows

## Working Model

- ChatGPT: orchestration, architecture, roadmap, coordination
- Claude Code / Codex: implementation and repository operations
- GitHub: persistent project memory and CI/CD
- VSCode: local operational environment

## Current Status

Project initialized.
Documentation bootstrap in progress.
