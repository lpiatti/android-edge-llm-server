# Daemon Stability and Platform Guidelines

This document provides formal architectural constraints, SDK choices, and technical implementation rules to ensure the Android-native LLM server remains "always active" and resilient against process termination (Low Memory Killer) and battery-saver throttling.

**All agents developing this repository MUST respect and implement these rules.**

---

## 1. Platform SDK Cut-off

To balance wide device compatibility (allowing users to repurpose older, dedicated hardware) with clean, modern APIs, the platform SDK targets are defined as follows:

| Parameter | Value | Rationale |
|---|---|---|
| **Minimum SDK** | **29 (Android 10.0)** | Sweet spot for recycling older devices (5+ years old). Avoids legacy pre-Oreo background restrictions, includes native scoped storage, and provides clean Modern Java/Kotlin runtime features. |
| **Target SDK** | **34 (Android 14.0)** | Complies with modern safety, permission models, precise Foreground Service type declarations (`foregroundServiceType`), and mandatory `POST_NOTIFICATIONS` runtime permissions. |

---

## 2. Dedicated Server Profile

The system is designed to run under a **Dedicated Server Profile**. Developers and agents can leverage the following environment guarantees to implement aggressive resource management policies:

1. **Continuous Power:** The device is permanently plugged into AC power. Battery conservation is a secondary concern compared to server responsiveness and CPU availability.
2. **Dedicated Network:** The device is permanently connected to high-performance, stable Wi-Fi.
3. **Low Application Footprint:** The phone runs minimal concurrent user-space applications (e.g., just the OS, the edge server, and system tools like Termux). This significantly lowers the likelihood of Low Memory Killer (LMK) eviction.

---

## 3. High-Resilience Background Strategies

To keep the edge server running 24/7, the following six layers of technical protection must be implemented:

### A. Foreground Service (FGS) with Persistent Notification
The server HTTP daemon and the LLM inference engine must run inside an Android `Service` promoted to a **Foreground Service** using `ServiceCompat.startForeground()`.
* **Requirement:** Must display a persistent, non-dismissible notification.
* **Metadata (Android 14+):** Must declare `android:foregroundServiceType="specialUse"` or another justified type (e.g., `dataSync`) in the `AndroidManifest.xml` alongside `<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />` and `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />`.

### B. Battery Optimization Whitelisting (Doze Mode Bypass)
Android's aggressive Doze Mode and App Standby will freeze background execution unless whitelisted.
* **Requirement:** Propose to the user to bypass battery optimization. The app must prompt the user using `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
* **Result:** Ensures the process remains in the high-priority whitelist and retains LAN network connectivity.

### C. CPU Partial WakeLock
When the screen turns off, the CPU goes into deep sleep, pausing LLM inference and HTTP responses.
* **Requirement:** Acquire a `PowerManager.WakeLock` with the flag `PowerManager.PARTIAL_WAKE_LOCK`.
* **Guidance:** The WakeLock must be bound to the Foreground Service's lifecycle. It should be acquired when the server starts and safely released when the server is explicitly turned off by the user.

### D. High-Performance WifiLock
By default, the Wi-Fi interface enters low-power power-save mode (PSM) when idle, increasing latency for LAN clients.
* **Requirement:** Acquire a `WifiManager.WifiLock` with the flag `WifiManager.WIFI_MODE_FULL_HIGH_PERF` (or `WIFI_MODE_FULL_LOW_LATENCY` on API 29+).
* **Guidance:** Keeps the Wi-Fi hardware fully active for instant request response.

### E. Self-Healing & Automatic Riavvio (Restart Lifecycle)
Should the OS kill the daemon due to extreme system-wide memory stress, the app must recover automatically:
1. **`START_STICKY`:** Return `START_STICKY` inside `onStartCommand()`. This tells the Android OS to restart the service as soon as resources become free.
2. **`BOOT_COMPLETED`:** Register a `BroadcastReceiver` for `Intent.ACTION_BOOT_COMPLETED` so the server daemon launches automatically when the smartphone reboots or power-cycles.
3. **Scheduled Watchdog:** Use `AlarmManager` or periodic inexact `WorkManager` tasks acting as a watchdog to verify if the server's HTTP thread is responsive; if not, restart the Foreground Service.

---

## 4. Defensive Memory Management (LMK Prevention)

Since LLM model loading and inference are extremely memory-intensive, agents must write highly defensive memory code:

1. **Strict Quantized Model Limits:** Standard consumer phones usually have 4GB, 6GB, or 8GB of RAM. The app should enforce strict recommendations:
   - For 4GB RAM devices: Max 1B parameter models, quantized to 4-bit (weights <= 800MB).
   - For 6GB/8GB RAM devices: Max 2B-3B parameter models, quantized to 4-bit (weights <= 1.8GB).
2. **Respond to `onTrimMemory`:** Implement `ComponentCallbacks2` and override `onTrimMemory(level)`.
   - **`TRIM_MEMORY_RUNNING_CRITICAL`** or **`TRIM_MEMORY_MODERATE`**: Immediately release non-essential caches, trigger aggressive garbage collection, and potentially unload models from memory if they are not actively running an inference session.
3. **Aggressive GC & Model Unloading:** Since Java/Kotlin GC can be delayed, explicitly clean references and call `System.gc()` after model unloading or after heavy, non-streamed inference cycles.
