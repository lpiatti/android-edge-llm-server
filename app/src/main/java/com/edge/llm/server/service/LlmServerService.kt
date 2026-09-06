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
import com.edge.llm.server.model.ChatMessage
import com.edge.llm.server.model.ModelManager
import com.edge.llm.server.model.PromptBuilder
import com.edge.llm.server.model.QueueFullException
import com.edge.llm.server.model.QueueTimeoutException
import com.edge.llm.server.model.RequestQueue
import com.edge.llm.server.util.ServerConsole
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.writeStringUtf8
import java.util.Date
import java.util.UUID
import kotlinx.serialization.Serializable

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

        // 1. Promote to Foreground Service
        startForegroundNotification()

        // 2. Acquire System Locks to secure AC-powered stability
        acquireLocks()

        // 3. Launch Ktor embedded HTTP Server
        startHttpServer()

        // Auto-load model configuration from SharedPreferences on service startup
        val prefs = getSharedPreferences("llm_server_prefs", Context.MODE_PRIVATE)
        val isMock = prefs.getBoolean("is_mock_engine", true)
        val isGpu = prefs.getBoolean("is_gpu_backend", false)
        val modelPath = prefs.getString("selected_model_path", null)
        
        if (!ModelManager.isModelLoaded && !ModelManager.isLoading) {
            ServerConsole.log("LlmServerService: Auto-restoring saved model configuration (isMock=$isMock, path=$modelPath, useGpu=$isGpu)...")
            Thread {
                try {
                    kotlinx.coroutines.runBlocking {
                        ModelManager.loadModel(modelPath, isMock, isGpu)
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
            serverEngine = embeddedServer(CIO, port = 8080, host = bindHost) {
                install(ContentNegotiation) {
                    json(kotlinx.serialization.json.Json {
                        prettyPrint = true
                        isLenient = true
                        ignoreUnknownKeys = true
                    })
                }
                
                routing {
                    // Endpoint: /health
                    get("/health") {
                        com.edge.llm.server.util.ServerStats.totalRequests++
                        val clientIp = call.request.local.remoteHost
                        ServerConsole.log("--> GET /health from $clientIp")
                        
                        val uptimeSec = (Date().time - startTime.time) / 1000
                        val response = HealthResponse(
                            status = "healthy",
                            server_time = Date().toString(),
                            uptime_seconds = uptimeSec
                        )
                        call.respond(response)
                        ServerConsole.log("<-- 200 OK (Uptime: $uptimeSec s)")
                    }

                    // Endpoint: /v1/models (OpenAI compatibility)
                    get("/v1/models") {
                        com.edge.llm.server.util.ServerStats.totalRequests++
                        val clientIp = call.request.local.remoteHost
                        ServerConsole.log("--> GET /v1/models from $clientIp")
                        
                        val response = ModelsResponse(
                            data = listOf(
                                ModelItem(id = ModelManager.activeModelName)
                            )
                        )
                        call.respond(response)
                        ServerConsole.log("<-- 200 OK (Active: ${ModelManager.activeModelName})")
                    }

                    // Endpoint: /v1/chat/completions (OpenAI Chat compatibility)
                    post("/v1/chat/completions") {
                        val clientIp = call.request.local.remoteHost
                        com.edge.llm.server.util.ServerStats.totalRequests++
                        com.edge.llm.server.util.ServerStats.activeConnections++
                        val requestTime = System.currentTimeMillis()
                        
                        val queue = activeRequestQueue ?: throw IllegalStateException("Request queue not initialized")
                        com.edge.llm.server.util.ServerStats.queuedRequests = queue.queuedRequestsCount

                        try {
                            if (!ModelManager.isModelLoaded) {
                                call.respond(
                                    HttpStatusCode.ServiceUnavailable,
                                    OpenAiErrorResponse(
                                        error = OpenAiError(
                                            message = "No model is currently loaded in engine. Please initialize a model first.",
                                            type = "server_error",
                                            code = 503
                                        )
                                    )
                                )
                                return@post
                            }

                            val req = call.receive<ChatCompletionRequest>()
                            ServerConsole.log("--> POST /v1/chat/completions (model=${req.model}, stream=${req.stream}, temp=${req.temperature}, top_p=${req.top_p}, max_tokens=${req.max_tokens}) from $clientIp")
                            
                            val chatMessages = req.messages.map { ChatMessage(role = it.role, content = it.content ?: "") }
                            val fullPrompt = PromptBuilder.buildGemmaPrompt(chatMessages)
                            ServerConsole.log("    Assembled prompt: ${chatMessages.size} turns, ${fullPrompt.length} chars")

                            val activeProvider = ModelManager.activeProvider
                            
                            if (req.stream == true) {
                                queue.executeStream(
                                    flowProvider = {
                                        activeProvider.generateStream(
                                            prompt = fullPrompt,
                                            temperature = req.temperature,
                                            topP = req.top_p,
                                            maxTokens = req.max_tokens
                                        )
                                    },
                                    consumer = { flow ->
                                        call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                                            val chunkId = "chatcmpl-" + UUID.randomUUID().toString()
                                            val createdTime = System.currentTimeMillis() / 1000
                                            
                                            try {
                                                var tokenCount = 0
                                                flow.collect { token ->
                                                    tokenCount++
                                                    com.edge.llm.server.util.ServerStats.totalTokensGenerated++
                                                    val chunk = ChatCompletionChunk(
                                                        id = chunkId,
                                                        created = createdTime,
                                                        model = ModelManager.activeModelName,
                                                        choices = listOf(
                                                            ChunkChoice(
                                                                index = 0,
                                                                delta = ChunkDelta(content = token),
                                                                finish_reason = null
                                                            )
                                                        )
                                                    )
                                                    val jsonString = kotlinx.serialization.json.Json.encodeToString(ChatCompletionChunk.serializer(), chunk)
                                                    writeStringUtf8("data: $jsonString\n\n")
                                                    flush()
                                                }
                                                val durationSec = (System.currentTimeMillis() - requestTime) / 1000.0
                                                if (durationSec > 0) {
                                                    com.edge.llm.server.util.ServerStats.lastGenerationSpeedTps = tokenCount / durationSec
                                                }
                                                // End of stream token
                                                val endChunk = ChatCompletionChunk(
                                                    id = chunkId,
                                                    created = createdTime,
                                                    model = ModelManager.activeModelName,
                                                    choices = listOf(
                                                        ChunkChoice(
                                                            index = 0,
                                                            delta = ChunkDelta(content = ""),
                                                            finish_reason = "stop"
                                                        )
                                                    )
                                                )
                                                val jsonString = kotlinx.serialization.json.Json.encodeToString(ChatCompletionChunk.serializer(), endChunk)
                                                writeStringUtf8("data: $jsonString\n\n")
                                                writeStringUtf8("data: [DONE]\n\n")
                                                flush()
                                                ServerConsole.log("<-- 200 OK (OpenAI Stream completed, $tokenCount tokens)")
                                            } catch (e: Exception) {
                                                ServerConsole.log("ERROR in stream collection: ${e.message}")
                                                writeStringUtf8("data: {\"error\": \"${e.message}\"}\n\n")
                                                flush()
                                            }
                                        }
                                    }
                                )
                            } else {
                                val answer = queue.execute {
                                    activeProvider.generate(
                                        prompt = fullPrompt,
                                        temperature = req.temperature,
                                        topP = req.top_p,
                                        maxTokens = req.max_tokens
                                    )
                                }
                                val tokenCount = answer.length / 4
                                com.edge.llm.server.util.ServerStats.totalTokensGenerated += tokenCount
                                val durationSec = (System.currentTimeMillis() - requestTime) / 1000.0
                                if (durationSec > 0) {
                                    com.edge.llm.server.util.ServerStats.lastGenerationSpeedTps = tokenCount / durationSec
                                }
                                
                                val estimatedPromptTokens = fullPrompt.length / 4
                                val response = ChatCompletionResponse(
                                    id = "chatcmpl-" + UUID.randomUUID().toString(),
                                    `object` = "chat.completion",
                                    created = System.currentTimeMillis() / 1000,
                                    model = ModelManager.activeModelName,
                                    choices = listOf(
                                        Choice(
                                            index = 0,
                                            message = Message(role = "assistant", content = answer),
                                            finish_reason = "stop"
                                        )
                                    ),
                                    usage = Usage(
                                        prompt_tokens = estimatedPromptTokens,
                                        completion_tokens = tokenCount,
                                        total_tokens = estimatedPromptTokens + tokenCount
                                    )
                                )
                                
                                call.respond(response)
                                ServerConsole.log("<-- 200 OK (OpenAI completions response sent)")
                            }
                        } catch (e: QueueFullException) {
                            ServerConsole.log("QUEUE OVERFLOW (429): ${e.message}")
                            call.response.header("Retry-After", "30")
                            call.respond(
                                HttpStatusCode.TooManyRequests,
                                OpenAiErrorResponse(
                                    error = OpenAiError(
                                        message = e.message ?: "Server is busy processing other requests. Queue capacity reached. Please retry later.",
                                        type = "rate_limit_exceeded",
                                        code = 429
                                    )
                                )
                            )
                        } catch (e: QueueTimeoutException) {
                            ServerConsole.log("QUEUE TIMEOUT (429): ${e.message}")
                            call.response.header("Retry-After", "30")
                            call.respond(
                                HttpStatusCode.TooManyRequests,
                                OpenAiErrorResponse(
                                    error = OpenAiError(
                                        message = e.message ?: "Request timed out waiting in inference queue.",
                                        type = "rate_limit_exceeded",
                                        code = 429
                                    )
                                )
                            )
                        } catch (e: Exception) {
                            val errorMsg = e.message ?: "Unknown parsing error"
                            ServerConsole.log("ERROR processing chat completions: $errorMsg")
                            call.respond(
                                HttpStatusCode.BadRequest,
                                OpenAiErrorResponse(
                                    error = OpenAiError(
                                        message = errorMsg,
                                        type = "invalid_request_error",
                                        code = 400
                                    )
                                )
                            )
                        } finally {
                            com.edge.llm.server.util.ServerStats.activeConnections--
                            activeRequestQueue?.let {
                                com.edge.llm.server.util.ServerStats.queuedRequests = it.queuedRequestsCount
                            }
                        }
                    }

                    // --- Ollama Compatibility Endpoints ---

                    // Endpoint: /api/tags (Ollama models list)
                    get("/api/tags") {
                        com.edge.llm.server.util.ServerStats.totalRequests++
                        val clientIp = call.request.local.remoteHost
                        ServerConsole.log("--> GET /api/tags from $clientIp")
                        
                        val response = OllamaTagsResponse(
                            models = listOf(
                                OllamaModelItem(name = ModelManager.activeModelName)
                            )
                        )
                        call.respond(response)
                        ServerConsole.log("<-- 200 OK (Active Ollama tag: ${ModelManager.activeModelName})")
                    }

                    // Endpoint: /api/chat (Ollama chat completion)
                    post("/api/chat") {
                        val clientIp = call.request.local.remoteHost
                        com.edge.llm.server.util.ServerStats.totalRequests++
                        com.edge.llm.server.util.ServerStats.activeConnections++
                        val requestTime = System.currentTimeMillis()
                        
                        val queue = activeRequestQueue ?: throw IllegalStateException("Request queue not initialized")
                        com.edge.llm.server.util.ServerStats.queuedRequests = queue.queuedRequestsCount

                        try {
                            if (!ModelManager.isModelLoaded) {
                                call.respond(
                                    HttpStatusCode.ServiceUnavailable,
                                    mapOf("error" to "No model is currently loaded in engine. Please initialize a model first.")
                                )
                                return@post
                            }

                            val req = call.receive<OllamaChatRequest>()
                            ServerConsole.log("--> POST /api/chat (model=${req.model}, stream=${req.stream}) from $clientIp")
                            
                            val chatMessages = req.messages.map { ChatMessage(role = it.role, content = it.content ?: "") }
                            val fullPrompt = PromptBuilder.buildGemmaPrompt(chatMessages)
                            ServerConsole.log("    Assembled Ollama prompt: ${chatMessages.size} turns, ${fullPrompt.length} chars")

                            val activeProvider = ModelManager.activeProvider
                            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault())

                            val reqTemp = req.options?.temperature
                            val reqTopP = req.options?.top_p
                            val reqMaxTokens = req.options?.num_predict

                            if (req.stream == true) {
                                queue.executeStream(
                                    flowProvider = {
                                        activeProvider.generateStream(
                                            prompt = fullPrompt,
                                            temperature = reqTemp,
                                            topP = reqTopP,
                                            maxTokens = reqMaxTokens
                                        )
                                    },
                                    consumer = { flow ->
                                        call.respondBytesWriter(contentType = ContentType.Application.Json) {
                                            try {
                                                var tokenCount = 0
                                                flow.collect { token ->
                                                    tokenCount++
                                                    com.edge.llm.server.util.ServerStats.totalTokensGenerated++
                                                    val chunk = OllamaChatResponse(
                                                        model = ModelManager.activeModelName,
                                                        created_at = formatter.format(java.util.Date()),
                                                        message = OllamaMessage(role = "assistant", content = token),
                                                        done = false,
                                                        total_duration = 0L
                                                    )
                                                    val jsonString = kotlinx.serialization.json.Json.encodeToString(OllamaChatResponse.serializer(), chunk)
                                                    writeStringUtf8("$jsonString\n")
                                                    flush()
                                                }
                                                val durationSec = (System.currentTimeMillis() - requestTime) / 1000.0
                                                if (durationSec > 0) {
                                                    com.edge.llm.server.util.ServerStats.lastGenerationSpeedTps = tokenCount / durationSec
                                                }
                                                // Final chunk
                                                val finalChunk = OllamaChatResponse(
                                                    model = ModelManager.activeModelName,
                                                    created_at = formatter.format(java.util.Date()),
                                                    message = OllamaMessage(role = "assistant", content = ""),
                                                    done = true,
                                                    total_duration = 1000000L
                                                )
                                                val jsonString = kotlinx.serialization.json.Json.encodeToString(OllamaChatResponse.serializer(), finalChunk)
                                                writeStringUtf8("$jsonString\n")
                                                flush()
                                                ServerConsole.log("<-- 200 OK (Ollama Stream completed, $tokenCount tokens)")
                                            } catch (e: Exception) {
                                                ServerConsole.log("ERROR in Ollama stream collection: ${e.message}")
                                                writeStringUtf8("{\"error\": \"${e.message}\"}\n")
                                                flush()
                                            }
                                        }
                                    }
                                )
                            } else {
                                val answer = queue.execute {
                                    activeProvider.generate(
                                        prompt = fullPrompt,
                                        temperature = reqTemp,
                                        topP = reqTopP,
                                        maxTokens = reqMaxTokens
                                    )
                                }
                                val tokenCount = answer.length / 4
                                com.edge.llm.server.util.ServerStats.totalTokensGenerated += tokenCount
                                val durationSec = (System.currentTimeMillis() - requestTime) / 1000.0
                                if (durationSec > 0) {
                                    com.edge.llm.server.util.ServerStats.lastGenerationSpeedTps = tokenCount / durationSec
                                }
                                
                                val response = OllamaChatResponse(
                                    model = ModelManager.activeModelName,
                                    created_at = formatter.format(java.util.Date()),
                                    message = OllamaMessage(role = "assistant", content = answer),
                                    done = true,
                                    total_duration = 1250000L
                                )
                                call.respond(response)
                                ServerConsole.log("<-- 200 OK (Ollama response sent)")
                            }
                        } catch (e: QueueFullException) {
                            ServerConsole.log("Ollama QUEUE OVERFLOW (429): ${e.message}")
                            call.response.header("Retry-After", "30")
                            call.respond(
                                HttpStatusCode.TooManyRequests,
                                mapOf("error" to (e.message ?: "Server is busy. Queue capacity reached. Please retry later."))
                            )
                        } catch (e: QueueTimeoutException) {
                            ServerConsole.log("Ollama QUEUE TIMEOUT (429): ${e.message}")
                            call.response.header("Retry-After", "30")
                            call.respond(
                                HttpStatusCode.TooManyRequests,
                                mapOf("error" to (e.message ?: "Request timed out waiting in inference queue."))
                            )
                        } catch (e: Exception) {
                            val errorMsg = e.message ?: "Unknown parsing error"
                            ServerConsole.log("ERROR processing Ollama chat: $errorMsg")
                            call.respond(HttpStatusCode.BadRequest, mapOf("error" to errorMsg))
                        } finally {
                            com.edge.llm.server.util.ServerStats.activeConnections--
                            activeRequestQueue?.let {
                                com.edge.llm.server.util.ServerStats.queuedRequests = it.queuedRequestsCount
                            }
                        }
                    }
                }
            }.start(wait = false)
            
            ServerConsole.log("Server listening successfully at http://$bindHost:8080")
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
        ServerConsole.log("LlmServerService destroyed successfully.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // --- JSON Serialization Data Classes (OpenAI-Compatible schemas) ---

    @Serializable
    data class OpenAiError(
        val message: String,
        val type: String = "rate_limit_exceeded",
        val code: Int = 429
    )

    @Serializable
    data class OpenAiErrorResponse(
        val error: OpenAiError
    )

    @Serializable
    data class HealthResponse(
        val status: String,
        val server_time: String,
        val uptime_seconds: Long
    )

    @Serializable
    data class ModelItem(
        val id: String,
        val `object`: String = "model",
        val owned_by: String = "local-system"
    )

    @Serializable
    data class ModelsResponse(
        val `object`: String = "list",
        val data: List<ModelItem>
    )

    @Serializable
    data class Message(
        val role: String,
        val content: String? = ""
    )

    @Serializable
    data class ChatCompletionRequest(
        val model: String,
        val messages: List<Message>,
        val temperature: Double? = 0.7,
        val top_p: Double? = null,
        val max_tokens: Int? = null,
        val stream: Boolean? = false
    )

    @Serializable
    data class Choice(
        val index: Int,
        val message: Message,
        val finish_reason: String
    )

    @Serializable
    data class Usage(
        val prompt_tokens: Int,
        val completion_tokens: Int,
        val total_tokens: Int
    )

    @Serializable
    data class ChatCompletionResponse(
        val id: String,
        val `object`: String,
        val created: Long,
        val model: String,
        val choices: List<Choice>,
        val usage: Usage
    )

    // --- JSON Serialization Chunks Data Classes (OpenAI-Compatible Streaming) ---

    @Serializable
    data class ChunkDelta(
        val content: String
    )

    @Serializable
    data class ChunkChoice(
        val index: Int,
        val delta: ChunkDelta,
        val finish_reason: String?
    )

    @Serializable
    data class ChatCompletionChunk(
        val id: String,
        val `object`: String = "chat.completion.chunk",
        val created: Long,
        val model: String,
        val choices: List<ChunkChoice>
    )

    // --- JSON Serialization Data Classes (Ollama-Compatible schemas) ---

    @Serializable
    data class OllamaModelItem(
        val name: String,
        val modified_at: String = "2026-05-31T12:00:00Z",
        val size: Long = 4700000000L,
        val digest: String = "sha256:mock"
    )

    @Serializable
    data class OllamaTagsResponse(
        val models: List<OllamaModelItem>
    )

    @Serializable
    data class OllamaMessage(
        val role: String,
        val content: String? = ""
    )

    @Serializable
    data class OllamaOptions(
        val temperature: Double? = null,
        val top_p: Double? = null,
        val num_predict: Int? = null
    )

    @Serializable
    data class OllamaChatRequest(
        val model: String,
        val messages: List<OllamaMessage>,
        val options: OllamaOptions? = null,
        val stream: Boolean? = false
    )

    @Serializable
    data class OllamaChatResponse(
        val model: String,
        val created_at: String,
        val message: OllamaMessage,
        val done: Boolean,
        val total_duration: Long
    )
}
