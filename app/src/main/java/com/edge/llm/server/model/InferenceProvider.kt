package com.edge.llm.server.model

import com.edge.llm.server.util.LogCategory
import com.edge.llm.server.util.ServerConsole
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
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
    suspend fun generate(
        prompt: String,
        temperature: Double? = null,
        topP: Double? = null,
        maxTokens: Int? = null
    ): String
    suspend fun generateStream(
        prompt: String,
        temperature: Double? = null,
        topP: Double? = null,
        maxTokens: Int? = null
    ): Flow<String>
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
        ServerConsole.log(LogCategory.ENGINE, "MockInferenceProvider initialized.")
        true
    }

    override suspend fun generate(
        prompt: String,
        temperature: Double?,
        topP: Double?,
        maxTokens: Int?
    ): String = withContext(Dispatchers.Default) {
        if (!isInit) throw IllegalStateException("Mock provider not initialized")
        ServerConsole.log(LogCategory.ENGINE, "Mock generate: prompt chars=${prompt.length}, temp=$temperature, top_p=$topP, max_tokens=$maxTokens")
        "Ciao! Ti rispondo dal Server Edge Android in modalità MOCK. Ho ricevuto il tuo prompt (${prompt.length} caratteri). Contesto multi-turn ricevuto con successo!"
    }

    override suspend fun generateStream(
        prompt: String,
        temperature: Double?,
        topP: Double?,
        maxTokens: Int?
    ): Flow<String> = flow {
        if (!isInit) throw IllegalStateException("Mock provider not initialized")
        ServerConsole.log(LogCategory.ENGINE, "Mock stream: prompt chars=${prompt.length}, temp=$temperature, top_p=$topP, max_tokens=$maxTokens")
        val fullResponse = "Ciao! Ti rispondo dal Server Edge Android in modalità STREAMING MOCK. Prompt multi-turn ricevuto (${prompt.length} caratteri)."
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
        ServerConsole.log(LogCategory.ENGINE, "MockInferenceProvider unloaded.")
    }
}

/**
 * LiteRtLmInferenceProvider: A concrete implementation wrapping Google AI Edge's LiteRT-LM native runtime,
 * executing LLM model inference and token flow generation asynchronously.
 */
class LiteRtLmInferenceProvider(
    private val modelPath: String,
    private val useGpu: Boolean = false,
    private val cacheDir: String? = null
) : InferenceProvider {
    private var engine: Engine? = null

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(modelPath)
            if (!file.exists()) {
                throw Exception("Model file does not exist at absolute path: $modelPath")
            }
            ServerConsole.log(LogCategory.ENGINE, "LiteRT-LM: Loading model from ${file.name} (size: ${file.length() / 1024 / 1024} MB, GPU=$useGpu, cacheDir=${cacheDir ?: "default"})...")
            
            // Ensure cache directory exists if specified
            if (!cacheDir.isNullOrEmpty()) {
                val dir = File(cacheDir)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }

            val backend = if (useGpu) com.google.ai.edge.litertlm.Backend.GPU() else com.google.ai.edge.litertlm.Backend.CPU()
            val config = EngineConfig(modelPath = modelPath, backend = backend, cacheDir = cacheDir)
            val newEngine = Engine(config)
            newEngine.initialize()
            engine = newEngine
            ServerConsole.log(LogCategory.ENGINE, "LiteRT-LM: Engine initialized successfully for ${file.name}!")
            true
        } catch (e: Exception) {
            ServerConsole.log(LogCategory.ENGINE, "CRITICAL: LiteRT-LM initialization failed: ${e.message}")
            throw e
        }
    }

    override suspend fun generate(
        prompt: String,
        temperature: Double?,
        topP: Double?,
        maxTokens: Int?
    ): String = withContext(Dispatchers.IO) {
        val activeEngine = engine ?: throw IllegalStateException("LiteRT-LM Engine not initialized or already unloaded")
        val resultBuilder = StringBuilder()
        val conversationConfig = if (temperature != null || topP != null) {
            val tempVal = temperature ?: 0.7
            val topPVal = topP ?: 0.95
            ServerConsole.log(LogCategory.ENGINE, "LiteRT-LM: Applying dynamic SamplerConfig (topK=40, temp=$tempVal, top_p=$topPVal)")
            ConversationConfig(samplerConfig = SamplerConfig(topK = 40, topP = topPVal, temperature = tempVal))
        } else {
            ConversationConfig()
        }
        
        ServerConsole.log(LogCategory.ENGINE, "LiteRT-LM: Running synchronous inference (prompt chars: ${prompt.length})...")
        activeEngine.createConversation(conversationConfig).use { conversation ->
            // Collect flow tokens synchronously into a string
            conversation.sendMessageAsync(prompt).collect { message ->
                resultBuilder.append(message.toString())
            }
        }
        val finalAnswer = resultBuilder.toString()
        ServerConsole.log(LogCategory.ENGINE, "LiteRT-LM: Synchronous inference complete (${finalAnswer.length} chars generated)")
        finalAnswer
    }

    override suspend fun generateStream(
        prompt: String,
        temperature: Double?,
        topP: Double?,
        maxTokens: Int?
    ): Flow<String> = flow {
        val activeEngine = engine ?: throw IllegalStateException("LiteRT-LM Engine not initialized or already unloaded")
        val conversationConfig = if (temperature != null || topP != null) {
            val tempVal = temperature ?: 0.7
            val topPVal = topP ?: 0.95
            ServerConsole.log(LogCategory.ENGINE, "LiteRT-LM: Applying dynamic SamplerConfig (topK=40, temp=$tempVal, top_p=$topPVal)")
            ConversationConfig(samplerConfig = SamplerConfig(topK = 40, topP = topPVal, temperature = tempVal))
        } else {
            ConversationConfig()
        }
        ServerConsole.log(LogCategory.ENGINE, "LiteRT-LM: Running streaming inference (prompt chars: ${prompt.length})...")
        activeEngine.createConversation(conversationConfig).use { conversation ->
            conversation.sendMessageAsync(prompt).collect { message ->
                emit(message.toString())
            }
        }
        ServerConsole.log(LogCategory.ENGINE, "LiteRT-LM: Streaming inference flow completed.")
    }.flowOn(Dispatchers.IO)

    override fun unload() {
        try {
            engine?.close()
            ServerConsole.log(LogCategory.ENGINE, "LiteRT-LM: Closed native engine reference.")
        } catch (e: Exception) {
            ServerConsole.log(LogCategory.ENGINE, "Error closing LiteRT-LM engine: ${e.message}")
        } finally {
            engine = null
        }
    }
}
