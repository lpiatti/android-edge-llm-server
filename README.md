# Android Edge LLM Server

An Android-native edge AI server focused on exposing local LLM inference through OpenAI and Ollama-compatible APIs. This application is designed to turn recycled or dedicated Android devices (running Android 10+) into lightweight, high-performance, local LAN server nodes.

**Status:** `v2` in progress (PR #9): full OpenAI/Ollama compatibility, tool calling, prefix reuse. Roadmap: [docs/roadmap.md](docs/roadmap.md).

## Documentation

| Document | Description |
|---|---|
| [STATE.md](STATE.md) | Current state and next step (IT). |
| [docs/roadmap.md](docs/roadmap.md) | Goal and milestones M0–M7 with acceptance criteria (IT). |
| [docs/api-contract.md](docs/api-contract.md) | HTTP contract and **compatibility matrix** — what works today, what is planned. |
| [docs/architecture.md](docs/architecture.md) | Components and boundaries (Foreground Service, queue, provider, UI). |
| [docs/daemon-stability-guidelines.md](docs/daemon-stability-guidelines.md) | Wakelocks, Doze, memory (LMK), self-healing. |
| [DECISIONI.md](DECISIONI.md) | Decision log (IT). |
| [docs/index.md](docs/index.md) | Full documentation index. |
| [AGENTS.md](AGENTS.md) | Operating contract for coding agents. |

## Core Features

*   **Decoupled Foreground Daemon:** Ktor server and LiteRT-LM runtime run inside a resilient Android Foreground Service (wake lock, Wi-Fi lock, restart on boot), independent from the UI.
*   **OpenAI & Ollama APIs:** `/v1/chat/completions`, `/v1/models`, `/api/chat`, `/api/generate`, `/api/tags`, `/api/show`, `/api/ps`, `/api/version`, with streaming (SSE / NDJSON), `stop`, `max_tokens`, JSON mode and usage. Exact coverage: [compatibility matrix](docs/api-contract.md#0-compatibility-matrix).
*   **Tool calling:** OpenAI `tools`/`tool_calls` and Ollama `tools`, using LiteRT-LM native function calling. The server returns the calls; your client executes them.
*   **Prefix reuse:** a follow-up request that extends the previous conversation reuses the KV cache, so only the new turn is prefilled.
*   **Optional API key** and **built-in tests** (benchmark, tool-call self-test) in the app.
*   **Serialized inference:** FIFO request queue, HTTP 429 with `Retry-After` on overflow.
*   **Direct model loading:** pick a `.litertlm` file, choose CPU or GPU backend, with RAM and SoC feasibility audit.
*   **Retro terminal UI:** pure programmatic Kotlin (no XML, no Compose), with built-in API tester and log console.
*   **LAN adapter binding:** listen on all interfaces, Wi-Fi only, or cellular only.
*   **In-app crash catcher:** stack traces shown on next launch, no ADB needed.

## Known Limitations (v2)

*   **Text only:** image/audio parts in `messages` are rejected (400).
*   **One model, one inference at a time:** requests are queued (HTTP 429 when the queue is full).
*   **Not supported (501):** embeddings, legacy `/v1/completions`, Ollama model management (`pull`, `create`, `delete`, ...).
*   **Plain HTTP:** intended for trusted LANs; set an API key in the DAEMON tab to require `Authorization: Bearer <key>`.
*   **Device verification pending:** see the compatibility matrix for what is verified on a real device.

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
