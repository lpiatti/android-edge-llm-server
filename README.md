# Android Edge LLM Server

An Android-native edge AI server focused on exposing local LLM inference through OpenAI and Ollama-compatible APIs. This application is designed to turn recycled or dedicated Android devices (running Android 10+) into lightweight, high-performance, local LAN server nodes.

**Project direction:** See [fable5/index.md](fable5/index.md) for the operational roadmap and architectural realignment resulting from the Fable 5 consultancy.

## Technical Documentation

To explore the architecture, guidelines, and technical history of this project, refer to the following documents:

| Document | Description |
|---|---|
| 🎯 [Operational Roadmap (Fable 5)](fable5/roadmap-sessioni.md) | 8-session phased execution plan (S1–S8) with Definition of Done and CI acceptance criteria. |
| 📋 [Project State](docs/project-state.md) | Current project phase, immediate milestones, and operational status. |
| 🗺️ [Roadmap](docs/roadmap.md) | Phased milestones from bootstrap to full background stabilization. |
| 🏛️ [Architecture Notes](docs/architecture.md) | Decoupled server boundaries (Background Foreground Service vs Programmatic UI). |
| 🛡️ [Daemon Stability Guidelines](docs/daemon-stability-guidelines.md) | Wakelocks, battery-saver bypasses, memory management (LMK), and self-healing. |
| 📜 [Architectural Decision Log (ADR)](docs/decision-log.md) | Chronological history of major design, SDK, and framework selections. |
| 🔬 [Garden Architecture Analysis](docs/garden-analysis.md) | Technical comparison between Google MediaPipe and LiteRT-LM runtimes. |
| 🤖 [Agent Operating Contract](AGENTS.md) | Development rules, branch workflows, and constraints for coding agents. |

## Core Features

*   **Decoupled Foreground Daemon:** The Ktor server and LiteRT-LM runtime run inside a high-resilience Android Foreground Service, remaining active even if the UI is closed.
*   **Zero-Overhead Retro UI:** Programmatically designed in pure Kotlin (no XML, no Compose) to minimize APK weight and build dependencies.
*   **OpenAI & Ollama compatible endpoints (see Known limitations):** Exposes `/v1/chat/completions` (OpenAI) and `/api/chat` (Ollama) endpoints, including Server-Sent Events (SSE) streaming.
*   **LAN Adapter Binding:** Dynamic IP interface selector (Wi-Fi, Cellular, All interfaces) to securely control bind bindings.
*   **Local Exception Catcher:** Intercepts runtime crashes and prints stack traces directly inside the app on subsequent launch to aid diagnostics without computer ADB access.

## Known Limitations (Current Phase — September 2026)

*   **Full-history processing:** Multi-turn history from the `messages` array is currently IN PROGRESS (targeted in Session S1 per [fable5/roadmap-sessioni.md](fable5/roadmap-sessioni.md)).
*   **Request queueing:** Serialized FIFO request queueing with HTTP 429 rejection on overflow is IN PROGRESS (Session S2).
*   **Tool calling:** Tool/function calling is IN PROGRESS (Sessions S5–S6).
*   **Static Web UI:** Browser-based control surface served by Ktor is IN PROGRESS (Session S7).
*   **Single concurrent inference:** The edge runtime supports only one inference execution at a time.
*   **Network profile:** Plain HTTP intended for trusted local LAN setups (no built-in TLS).

## Tested Models

This server runtime has been successfully tested on physical hardware (Android 10 & Android 14 test devices) loading and running:
*   **Gemma 4-E2B-it** ([google/gemma-4-E2B-it](https://huggingface.co/google/gemma-4-E2B-it)) quantized in `.litertlm` format, optimized by the Hugging Face [litert-community](https://huggingface.co/litert-community) organization.

## Quick API Test

Once the server daemon is started, you can run raw HTTP completion tests from any device on your local network:

### OpenAI Chat Completion `/v1/chat/completions`

```bash
curl http://<ANDROID_DEVICE_IP>:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4-E2B-it",
    "messages": [
      {"role": "user", "content": "Hello!"}
    ],
    "temperature": 0.7
  }'
```

### Ollama Chat `/api/chat`

```bash
curl http://<ANDROID_DEVICE_IP>:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4-E2B-it",
    "messages": [
      {"role": "user", "content": "Why is the sky blue?"}
    ],
    "stream": false
  }'
```

## Licensing

This repository is licensed under the **PolyForm Noncommercial License 1.0.0**.
You are free to use, modify, share, and build upon this software for any **noncommercial** purpose — personal projects, research, education, and nonprofit use. Commercial use rights remain reserved to the author. Contributions are welcome: by submitting a contribution you agree it is licensed to the project owner under the same terms. See the [LICENSE](LICENSE) file for the full text.
