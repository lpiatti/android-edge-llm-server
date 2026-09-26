package com.edge.llm.server.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.edge.llm.server.api.ApiKeyStore
import com.edge.llm.server.api.edgeApiModule
import com.edge.llm.server.model.ModelManager
import com.edge.llm.server.model.RequestQueue
import com.edge.llm.server.util.ServerConsole
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import java.util.Date

/**
 * LlmServerService: The background daemon service running 24/7 on AC power.
 * It encapsulates a Ktor embedded HTTP server, CPU/WiFi locks, and persistent notifications.
 */
class LlmServerService : Service() {

    private var serverEngine: EmbeddedServer<*, *>? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var bindHost = "0.0.0.0"
    
    private val startTime = Date()

    companion object {
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "llm_server_channel"
        const val CHANNEL_NAME = "Edge LLM Daemon Service"
        const val ACTION_STOP = "com.edge.llm.server.service.ACTION_STOP"
        
        @Volatile
        var isServiceRunning = false
            private set

        @Volatile
        var activeBindHost = "0.0.0.0"
            internal set

        @Volatile
        var activeRequestQueue: RequestQueue? = null
            internal set
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize persistent logs file in ServerConsole
        ServerConsole.logFile = java.io.File(filesDir, "persistent_logs.txt")
        
        // Global Uncaught Exception Handler to capture background thread crash logs
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val file = java.io.File(filesDir, "crash_log.txt")
                java.io.FileOutputStream(file).use { fos ->
                    java.io.PrintStream(fos).use { ps ->
                        ps.println("CRASH REPORT (SERVICE) - ${java.util.Date()}")
                        ps.println("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})")
                        throwable.printStackTrace(ps)
                    }
                }
            } catch (e: Exception) {
                // Ignore writing error
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
        
        ServerConsole.log("Initializing LlmServerService...")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ServerConsole.log("Received STOP action from notification. Shutting down daemon...")
            stopSelf()
            return START_NOT_STICKY
        }

        if (isServiceRunning) {
            ServerConsole.log("Service is already active, ignoring start intent.")
            return START_STICKY
        }
        
        isServiceRunning = true
        bindHost = intent?.getStringExtra("EXTRA_BIND_HOST") ?: "0.0.0.0"
        activeBindHost = bindHost
        ServerConsole.log("Starting Foreground Service Daemon bound to $bindHost...")
        
        // Reset telemetry stats on startup
        com.edge.llm.server.util.ServerStats.reset()

        // Initialize single-worker FIFO RequestQueue (depth 4, timeout 120s)
        activeRequestQueue = RequestQueue(maxQueueDepth = 4, queueTimeoutMs = 120_000L)
        ServerConsole.log("RequestQueue initialized (depth=4, timeout=120s)")

        val prefs = getSharedPreferences("llm_server_prefs", Context.MODE_PRIVATE)
        ApiKeyStore.apiKey = prefs.getString("api_key", null)?.trim()?.ifEmpty { null }

        // 1. Promote to Foreground Service
        startForegroundNotification()

        // 2. Acquire System Locks to secure AC-powered stability
        acquireLocks()

        // 3. Launch Ktor embedded HTTP Server
        startHttpServer()

        // Auto-load model configuration from SharedPreferences on service startup
        val isMock = prefs.getBoolean("is_mock_engine", true)
        val isGpu = prefs.getBoolean("is_gpu_backend", false)
        val modelPath = prefs.getString("selected_model_path", null)
        val maxNumTokens = prefs.getInt("max_num_tokens", 0).takeIf { it > 0 }

        if (!isMock && ModelManager.hasStaleLoadingMarker(filesDir)) {
            // The last native load killed the process: do not retry automatically (crash loop on boot).
            ServerConsole.log("LlmServerService: Previous model load was interrupted (likely OOM). Auto-restore skipped; load the model manually from the ENGINE tab.")
        } else if (!ModelManager.isModelLoaded && !ModelManager.isLoading) {
            ServerConsole.log("LlmServerService: Auto-restoring saved model configuration (isMock=$isMock, path=$modelPath, useGpu=$isGpu)...")
            Thread {
                try {
                    kotlinx.coroutines.runBlocking {
                        val cacheDir = ModelManager.getPrivateCacheDirectory(this@LlmServerService).absolutePath
                        ModelManager.loadModel(modelPath, isMock, isGpu, cacheDir, maxNumTokens)
                    }
                    ServerConsole.log("LlmServerService: Saved model auto-restored successfully.")
                } catch (e: Exception) {
                    val errorMsg = e.message ?: e.toString()
                    ServerConsole.log("LlmServerService: Failed to auto-restore saved model: $errorMsg")
                    
                    // Write failure to crash_log.txt so it shows in UI
                    try {
                        val file = java.io.File(filesDir, "crash_log.txt")
                        java.io.FileOutputStream(file).use { fos ->
                            java.io.PrintStream(fos).use { ps ->
                                ps.println("ENGINE AUTO-RESTORE FAILURE - ${java.util.Date()}")
                                ps.println("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})")
                                ps.println("Error: $errorMsg")
                                if (isGpu) {
                                    ps.println("\nTip: Auto-restore failed in GPU mode. Try launching CPU mode in UI.")
                                }
                            }
                        }
                    } catch (ex: Exception) {}
                }
            }.start()
        }

        return START_STICKY
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val levelName = when (level) {
            TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
            TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
            TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
            TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
            TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
            TRIM_MEMORY_MODERATE -> "MODERATE"
            TRIM_MEMORY_COMPLETE -> "COMPLETE"
            else -> "UNKNOWN ($level)"
        }
        ServerConsole.log("⚠️ MEMORY ALERT: Service received onTrimMemory(level=$levelName).")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        ServerConsole.log("🚨 CRITICAL ALERT: Service received onLowMemory()!")
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the local LLM server daemon alive permanently."
            }
            manager.createNotificationChannel(channel)
        }

        // Tap content intent to open MainActivity
        val pm = packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
        val contentPendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.app.PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
        )

        // Action intent to stop the daemon
        val stopIntent = Intent(this, LlmServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = android.app.PendingIntent.getService(
            this,
            0,
            stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Edge LLM Server: ACTIVE")
            .setContentText("Listening on http://$bindHost:8080. Tap to configure.")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel, 
                "Stop Server", 
                stopPendingIntent
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        ServerConsole.log("Foreground notification posted successfully with Spotify-style close controls.")
    }

    private fun acquireLocks() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EdgeLlmServer::CpuWakeLock").apply {
            acquire()
        }
        ServerConsole.log("Acquired CPU Partial WakeLock.")

        val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "EdgeLlmServer::WifiLock")
        } else {
            @Suppress("DEPRECATION")
            wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "EdgeLlmServer::WifiLock")
        }.apply {
            acquire()
        }
        ServerConsole.log("Acquired High-Performance WiFi Lock.")
    }

    private fun startHttpServer() {
        ServerConsole.log("Spinning up embedded Ktor HTTP server on $bindHost...")
        try {
            val appVersion = try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "dev"
            } catch (e: Exception) {
                "dev"
            }
            val started = startTime.time
            serverEngine = embeddedServer(CIO, port = 8080, host = bindHost) {
                edgeApiModule(appVersion, started) { activeRequestQueue }
            }.start(wait = false)
            ServerConsole.log("Server listening successfully at http://$bindHost:8080 (API key ${if (ApiKeyStore.isEnabled) "REQUIRED" else "not set: open access"})")
        } catch (e: Exception) {
            ServerConsole.log("CRITICAL ERROR: Failed to launch HTTP server: ${e.message}")
        }
    }

    private fun releaseLocks() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            ServerConsole.log("Released CPU Partial WakeLock.")
        }
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
            ServerConsole.log("Released WiFi Lock.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        activeBindHost = "0.0.0.0"
        ServerConsole.log("Stopping HTTP Server...")
        try {
            serverEngine?.stop(1000, 2000)
            ServerConsole.log("HTTP Server stopped successfully.")
        } catch (e: Exception) {
            ServerConsole.log("Error stopping server engine: ${e.message}")
        }
        
        releaseLocks()
        activeRequestQueue = null
        try {
            ModelManager.purgeCacheFiles(this)
            ModelManager.trimMemoryAndCollectGarbage()
        } catch (e: Exception) {
            ServerConsole.log("Warning cleaning cache on service destroy: ${e.message}")
        }
        ServerConsole.log("LlmServerService destroyed successfully.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
