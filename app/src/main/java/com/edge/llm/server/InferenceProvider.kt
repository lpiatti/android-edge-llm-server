package com.edge.llm.server

import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * InferenceProvider: Interface defining model initialization and text generation contracts,
 * enabling decoupling of underlying runtime engines from HTTP server routes.
 */
interface InferenceProvider {
    suspend fun initialize(): Boolean
    suspend fun generate(prompt: String): String
    suspend fun generateStream(prompt: String): Flow<String>
    fun unload()
}

/**
 * MockInferenceProvider: Implements a simulated model inference engine, returning pre-packaged responses
 * immediately or in streaming chunks for testing and development.
 */
class MockInferenceProvider : InferenceProvider {
    private var isInit = false

    override suspend fun initialize(): Boolean = withContext(Dispatchers.Default) {
        isInit = true
        ServerConsole.log("MockInferenceProvider initialized.")
        true
    }

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.Default) {
        if (!isInit) throw IllegalStateException("Mock provider not initialized")
        "Ciao! Ti rispondo dal Server Edge Android in modalità MOCK. Ho ricevuto il tuo prompt: \"$prompt\". Tutto funziona a dovere!"
    }

    override suspend fun generateStream(prompt: String): Flow<String> = flow {
        if (!isInit) throw IllegalStateException("Mock provider not initialized")
        val fullResponse = "Ciao! Ti rispondo dal Server Edge Android in modalità STREAMING MOCK. Ho ricevuto il tuo prompt: \"$prompt\"."
        // Split full response by spaces to simulate token by token streaming
        val words = fullResponse.split(" ")
        for (i in words.indices) {
            val token = words[i] + if (i == words.size - 1) "" else " "
            emit(token)
            delay(100) // 100ms delay per word to simulate real token RTT
        }
    }.flowOn(Dispatchers.Default)

    override fun unload() {
        isInit = false
        ServerConsole.log("MockInferenceProvider unloaded.")
    }
}

/**
 * LiteRtLmInferenceProvider: A concrete implementation wrapping Google AI Edge's LiteRT-LM native runtime,
 * executing LLM model inference and token flow generation asynchronously.
 */
class LiteRtLmInferenceProvider(private val modelPath: String) : InferenceProvider {
    private var engine: Engine? = null

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(modelPath)
            if (!file.exists()) {
                throw Exception("Model file does not exist at absolute path: $modelPath")
            }
            ServerConsole.log("LiteRT-LM: Loading model from ${file.name} (size: ${file.length() / 1024 / 1024} MB)...")
            
            val config = EngineConfig(modelPath)
            val newEngine = Engine(config)
            newEngine.initialize()
            engine = newEngine
            ServerConsole.log("LiteRT-LM: Engine initialized successfully for ${file.name}!")
            true
        } catch (e: Exception) {
            ServerConsole.log("CRITICAL: LiteRT-LM initialization failed: ${e.message}")
            throw e
        }
    }

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        val activeEngine = engine ?: throw IllegalStateException("LiteRT-LM Engine not initialized or already unloaded")
        val resultBuilder = StringBuilder()
        
        ServerConsole.log("LiteRT-LM: Running synchronous inference...")
        activeEngine.createConversation().use { conversation ->
            // Collect flow tokens synchronously into a string
            conversation.sendMessageAsync(prompt).collect { token ->
                resultBuilder.append(token)
            }
        }
        val finalAnswer = resultBuilder.toString()
        ServerConsole.log("LiteRT-LM: Synchronous inference complete (${finalAnswer.length} chars generated)")
        finalAnswer
    }

    override suspend fun generateStream(prompt: String): Flow<String> = flow {
        val activeEngine = engine ?: throw IllegalStateException("LiteRT-LM Engine not initialized or already unloaded")
        ServerConsole.log("LiteRT-LM: Running streaming inference...")
        activeEngine.createConversation().use { conversation ->
            conversation.sendMessageAsync(prompt).collect { token ->
                emit(token)
            }
        }
        ServerConsole.log("LiteRT-LM: Streaming inference flow completed.")
    }.flowOn(Dispatchers.IO)

    override fun unload() {
        try {
            engine?.close()
            ServerConsole.log("LiteRT-LM: Closed native engine reference.")
        } catch (e: Exception) {
            ServerConsole.log("Error closing LiteRT-LM engine: ${e.message}")
        } finally {
            engine = null
        }
    }
}
