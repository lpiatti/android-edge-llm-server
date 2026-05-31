# Architecture and Design Decision Log (ADR)

This document contains a chronological registry of the primary technical design, architecture, platform, UI, and environment decisions made since the inception of the Android Edge LLM Server project. All participating developers and coding agents must align their changes with these decisions.

---

## 1. Platform SDK Selection
* **Date:** 2026-05-28
* **Decision:** Establish **Target SDK 34 (Android 14)** and **Minimum SDK 29 (Android 10.0)**.
* **Context:** The server runtime must be capable of being deployed on older consumer smartphones (allowing users to recycle older devices that are 5+ years old). 
* **Rationale:** API 29 provides modern Java/Kotlin API features and avoids legacy background restrictions (pre-Oreo limits), while targeting API 34 complies with modern Android security models, runtime notifications permission (`POST_NOTIFICATIONS`), and precise foreground service types declarations.

---

## 2. Dedicated Server Profile
* **Date:** 2026-05-28
* **Decision:** Optimize resource management policies under a **Dedicated Server Profile**.
* **Context:** Typical Android apps assume mobile, on-the-go battery conservation.
* **Rationale:** This application is designed to run on a device that is:
  1. Permanently plugged into AC power (making battery conservation a secondary concern).
  2. Permanently connected to high-performance Wi-Fi.
  3. Running a minimal application footprint (dedicated hosting device).
  Consequently, we can leverage aggressive locks and low-latency network bindings that would be disallowed in consumer-facing battery-saver applications.

---

## 3. Programmatic Vanilla Kotlin UI (Zero XML / Zero Compose)
* **Date:** 2026-05-29
* **Decision:** Build the entire Android UI (including bottom tabs and retro console logs) dynamically in pure programmatic Kotlin code inside `MainActivity.kt`.
* **Context:** Standard Android projects rely heavily on heavy XML layout sheets or Jetpack Compose declarative layouts.
* **Rationale:** Dynamic, programmatic Kotlin views require **zero** XML bindings, **zero** Compose compiler plugins, and zero heavyweight styling library dependencies. This guarantees:
  1. A microscopic APK bundle size (weights < 2.5MB).
  2. High build reproducibility (avoids Compose-compiler mismatch build failures).
  3. Absolute programmatic control over layouts, styling, and thread safety.

---

## 4. High-Resilience Background Daemon Service (FGS)
* **Date:** 2026-05-30
* **Decision:** Run the HTTP Ktor engine inside an Android `Service` promoted to a **Foreground Service (FGS)** with a persistent, non-dismissible notification and aggressive wake/network locks.
* **Context:** The Android OS aggressively suspends background threads to save power when the device screen turns off or goes into Doze mode.
* **Rationale:**
  - **FGS Type:** Declared as `foregroundServiceType="specialUse"` (API 34 compliant) with the required manifest permissions.
  - **Locks:** Acquired `PowerManager.PARTIAL_WAKE_LOCK` to keep the CPU running indefinitely, and high-performance `WifiManager.WifiLock` (`WIFI_MODE_FULL_LOW_LATENCY` / `WIFI_MODE_FULL_HIGH_PERF`) to keep the Wi-Fi hardware fully active.
  - **Self-Healing:** Overrode `onStartCommand` to return `START_STICKY`, reboot recovery via `BootReceiver` (listening to `BOOT_COMPLETED`), ensuring the server runs 24/7.

---

## 5. Decoupled Architecture (UI vs. Runtime Engine)
* **Date:** 2026-05-30
* **Decision:** Enforce total modular separation between `MainActivity` and `LlmServerService`.
* **Context:** Tight coupling makes inference systems brittle and dependent on app lifecycle.
* **Rationale:** Ktor's background server engine and CPU/WiFi locks reside exclusively inside the service lifecycle. If the user swipes `MainActivity` away, the HTTP server continues to run flawlessly. State is exchanged only through persistent thread-safe Singletons (`ServerConsole.kt`) and Start Intents.

---

## 6. Remote GitHub Actions CI/CD Verification Model
* **Date:** 2026-05-30
* **Decision:** Disable local Gradle build commands and execute all compilation validations exclusively through **GitHub Actions CI/CD**.
* **Context:** The local host environment does not contain a Java JDK, Gradle wrapper, or Android SDK toolset.
* **Rationale:** Trying to run local gradle compiles in the workspace returns command-not-found errors. We established a strict Git feature branch workflow where files are modified locally, committed, pushed to a branch, and validated via pull request compiler runs in GitHub.

---

## 7. Ktor CIO Engine & Mock Endpoints
* **Date:** 2026-05-31
* **Decision:** Embed Ktor using the lightweight `CIO` engine and implement fake OpenAI and Ollama routes.
* **Context:** Validating the background architecture before importing heavyweight C++ LLM libraries.
* **Rationale:** We integrated Ktor with `CIO` and kotlinx serialization to expose `/health`, `/v1/models`, `/v1/chat/completions` (OpenAI format), `/api/tags`, and `/api/chat` (Ollama format). This permits testing client apps (e.g. ChatUIs) against our server today, validating edge server responsiveness.

---

## 8. Network Interface Sockets Binding Selector
* **Date:** 2026-05-31
* **Decision:** Add interface selector buttons in Tab 1 (Wi-Fi Only, Mobile Only, All Interfaces) to control Ktor CIO bind targets.
* **Context:** By default, binding Ktor to `0.0.0.0` listens on all ports. However, some advanced servers need to bind exclusively to a specific adapter (e.g., to prevent exposing APIs on cellular connections while on Wi-Fi).
* **Rationale:** Added dynamic pill buttons that resolve matching adapter IPs (e.g., `wlan` for Wi-Fi, `rmnet`/`ccmni`/`ppp` for mobile cell networks). When starting the daemon, we dispatch the resolved IP address as `EXTRA_BIND_HOST` inside the service startup Intent, which Ktor uses to establish its listen socket. We block changes to selection while the server is active to prevent socket state discrepancies.

---

## 9. Diagnostic RTT Latency Cards and Raw HTTP Frame Dumps
* **Date:** 2026-05-31
* **Decision:** Structure the Tab 2 ("Test") console to output full raw HTTP request/response frame dumps.
* **Context:** API testers often output just the final JSON string, masking the HTTP exchange mechanics.
* **Rationale:** To make the built-in Client Test Harness highly instructional and professional, we format and dump a detailed `>>> HTTP REQUEST DUMP` (verb, path, headers, payload sizes) and matching `<<< RESPONSE RECEIVED` blocks in monospaced bright green font. The harness automatically routes diagnostics to Ktor's exact active binding IP to bypass connection refused socket restrictions.
