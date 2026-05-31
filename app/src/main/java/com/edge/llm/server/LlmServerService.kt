package com.edge.llm.server

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
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.Date
import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * LlmServerService: The background daemon service running 24/7 on AC power.
 * It encapsulates a Ktor embedded HTTP server, CPU/WiFi locks, and persistent notifications.
 */
class LlmServerService : Service() {

    private var serverEngine: ApplicationEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    
    private val startTime = Date()

    companion object {
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "llm_server_channel"
        const val CHANNEL_NAME = "Edge LLM Daemon Service"
        
        @Volatile
        var isServiceRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        ServerConsole.log("Initializing LlmServerService...")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isServiceRunning) {
            ServerConsole.log("Service is already active, ignoring start intent.")
            return START_STICKY
        }
        
        isServiceRunning = true
        ServerConsole.log("Starting Foreground Service Daemon...")
        
        // 1. Promote to Foreground Service
        startForegroundNotification()

        // 2. Acquire System Locks to secure AC-powered stability
        acquireLocks()

        // 3. Launch Ktor embedded HTTP Server
        startHttpServer()

        return START_STICKY
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

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Edge LLM Server: ACTIVE")
            .setContentText("Listening on LAN port 8080. Tap to configure.")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        ServerConsole.log("Foreground notification posted successfully.")
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
        ServerConsole.log("Spinning up embedded Ktor HTTP server...")
        try {
            serverEngine = embeddedServer(CIO, port = 8080, host = "0.0.0.0") {
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
                        val clientIp = call.request.local.remoteHost
                        ServerConsole.log("--> GET /v1/models from $clientIp")
                        
                        val response = ModelsResponse(
                            data = listOf(
                                ModelItem(id = "meta-llama-3-8b-instruct"),
                                ModelItem(id = "gemma-2b-it")
                            )
                        )
                        call.respond(response)
                        ServerConsole.log("<-- 200 OK (Returned 2 models)")
                    }

                    // Endpoint: /v1/chat/completions (OpenAI Chat compatibility)
                    post("/v1/chat/completions") {
                        val clientIp = call.request.local.remoteHost
                        try {
                            val req = call.receive<ChatCompletionRequest>()
                            ServerConsole.log("--> POST /v1/chat/completions (model=${req.model}) from $clientIp")
                            
                            val lastUserMsg = req.messages.lastOrNull { it.role == "user" }?.content ?: ""
                            ServerConsole.log("    User: \"$lastUserMsg\"")

                            // Generate realistic mocked completion matching standard payload
                            val fakeAnswer = "Ciao! Ti rispondo dal Server Edge Android locale in esecuzione sul telefono. Ho ricevuto il tuo messaggio: \"$lastUserMsg\". Lo scheletro del demone HTTP funziona ed è pronto!"
                            
                            val response = ChatCompletionResponse(
                                id = "chatcmpl-" + UUID.randomUUID().toString(),
                                `object` = "chat.completion",
                                created = System.currentTimeMillis() / 1000,
                                model = req.model,
                                choices = listOf(
                                    Choice(
                                        index = 0,
                                        message = Message(role = "assistant", content = fakeAnswer),
                                        finish_reason = "stop"
                                    )
                                ),
                                usage = Usage(
                                    prompt_tokens = 24,
                                    completion_tokens = 46,
                                    total_tokens = 70
                                )
                            )
                            
                            call.respond(response)
                            ServerConsole.log("<-- 200 OK (Assistant response sent)")
                        } catch (e: Exception) {
                            val errorMsg = e.message ?: "Unknown parsing error"
                            ServerConsole.log("ERROR processing chat completions: $errorMsg")
                            call.respond(io.ktor.http.HttpStatusCode.BadRequest, mapOf("error" to errorMsg))
                        }
                    }

                    // --- Ollama Compatibility Endpoints ---

                    // Endpoint: /api/tags (Ollama models list)
                    get("/api/tags") {
                        val clientIp = call.request.local.remoteHost
                        ServerConsole.log("--> GET /api/tags from $clientIp")
                        
                        val response = OllamaTagsResponse(
                            models = listOf(
                                OllamaModelItem(name = "llama3:latest"),
                                OllamaModelItem(name = "gemma:2b")
                            )
                        )
                        call.respond(response)
                        ServerConsole.log("<-- 200 OK (Returned 2 Ollama tags)")
                    }

                    // Endpoint: /api/chat (Ollama chat completion)
                    post("/api/chat") {
                        val clientIp = call.request.local.remoteHost
                        try {
                            val req = call.receive<OllamaChatRequest>()
                            ServerConsole.log("--> POST /api/chat (model=${req.model}) from $clientIp")
                            
                            val lastUserMsg = req.messages.lastOrNull { it.role == "user" }?.content ?: ""
                            ServerConsole.log("    User: \"$lastUserMsg\"")

                            val fakeAnswer = "Ciao! Risposta dal server Edge locale in formato Ollama. Ho ricevuto il tuo messaggio: \"$lastUserMsg\""
                            
                            val response = OllamaChatResponse(
                                model = req.model,
                                created_at = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault()).format(java.util.Date()),
                                message = OllamaMessage(role = "assistant", content = fakeAnswer),
                                done = true,
                                total_duration = 1250000L
                            )
                            call.respond(response)
                            ServerConsole.log("<-- 200 OK (Ollama response sent)")
                        } catch (e: Exception) {
                            val errorMsg = e.message ?: "Unknown parsing error"
                            ServerConsole.log("ERROR processing Ollama chat: $errorMsg")
                            call.respond(io.ktor.http.HttpStatusCode.BadRequest, mapOf("error" to errorMsg))
                        }
                    }
                }
            }.start(wait = false)
            
            ServerConsole.log("Server listening successfully at http://0.0.0.0:8080")
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
        ServerConsole.log("Stopping HTTP Server...")
        try {
            serverEngine?.stop(1000, 2000)
            ServerConsole.log("HTTP Server stopped successfully.")
        } catch (e: Exception) {
            ServerConsole.log("Error stopping server engine: ${e.message}")
        }
        
        releaseLocks()
        ServerConsole.log("LlmServerService destroyed successfully.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // --- JSON Serialization Data Classes (OpenAI-Compatible schemas) ---

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
        val content: String
    )

    @Serializable
    data class ChatCompletionRequest(
        val model: String,
        val messages: List<Message>,
        val temperature: Double? = 0.7,
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
        val content: String
    )

    @Serializable
    data class OllamaChatRequest(
        val model: String,
        val messages: List<OllamaMessage>,
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
