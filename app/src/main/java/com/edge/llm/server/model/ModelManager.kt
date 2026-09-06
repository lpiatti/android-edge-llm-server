package com.edge.llm.server.model

import android.os.Environment
import com.edge.llm.server.util.LogCategory
import com.edge.llm.server.util.ServerConsole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ModelManager: A thread-safe global singleton that owns the model lifecycle at application level,
 * resolving local storage directory weights, listing discovered models, and swapping active providers.
 */
object ModelManager {

    @Volatile
    var activeProvider: InferenceProvider = MockInferenceProvider()
        private set

    @Volatile
    var activeModelName: String = "mock-model"
        private set

    @Volatile
    var isMockMode: Boolean = true
        private set

    @Volatile
    var isModelLoaded: Boolean = false
        private set

    @Volatile
    var isLoading: Boolean = false
        private set

    @Volatile
    var isGpuActive: Boolean = false
        private set

    @Volatile
    var loadingError: String? = null
        private set

    /**
     * Resolves the target server models public directory: /sdcard/Download/llm-server/models/
     */
    fun getModelsDirectory(): File {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val serverDir = File(downloadDir, "llm-server/models")
        if (!serverDir.exists()) {
            try {
                serverDir.mkdirs()
            } catch (e: Exception) {
                // Ignore failure (permission might be missing yet)
            }
        }
        return serverDir
    }

    /**
     * Scans the models directory and the raw Downloads directory for files ending in ".litertlm".
     * Returns a list of discovered files.
     */
    fun listLocalModels(): List<File> {
        val filesList = mutableListOf<File>()
        
        // 1. Scan /sdcard/Download/llm-server/models/
        val serverDir = getModelsDirectory()
        if (serverDir.exists() && serverDir.isDirectory) {
            val files = serverDir.listFiles { _, name -> name.endsWith(".litertlm") }
            if (files != null) {
                filesList.addAll(files)
            }
        }

        // 2. Scan /sdcard/Download/ as a broad fallback
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadDir.exists() && downloadDir.isDirectory) {
            val files = downloadDir.listFiles { _, name -> name.endsWith(".litertlm") }
            if (files != null) {
                filesList.addAll(files)
            }
        }

        return filesList.distinctBy { it.absolutePath }
    }

    /**
     * Loads either the mock provider or initializes the real LiteRT-LM engine.
     * Operates strictly on Dispatchers.IO to protect foreground/main thread responsiveness.
     */
    suspend fun loadModel(
        modelPath: String?,
        useMock: Boolean,
        useGpu: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        isLoading = true
        isGpuActive = false
        loadingError = null
        val startTime = System.currentTimeMillis()
        ServerConsole.log(LogCategory.ENGINE, "ModelManager: Swapping model state (useMock=$useMock, path=$modelPath, useGpu=$useGpu)...")
        
        try {
            // Unload whatever is currently active
            activeProvider.unload()
            
            if (useMock) {
                val mockProvider = MockInferenceProvider()
                mockProvider.initialize()
                activeProvider = mockProvider
                activeModelName = "mock-model"
                isMockMode = true
                isModelLoaded = true
                isGpuActive = false
                ServerConsole.log(LogCategory.ENGINE, "ModelManager: Successfully swapped to Mock Model Mode.")
            } else {
                if (modelPath.isNullOrEmpty()) {
                    throw IllegalArgumentException("LiteRT-LM requires a valid model file path")
                }
                val realProvider = LiteRtLmInferenceProvider(modelPath, useGpu)
                realProvider.initialize() // Can throw exception if file invalid
                activeProvider = realProvider
                activeModelName = File(modelPath).name
                isMockMode = false
                isModelLoaded = true
                isGpuActive = useGpu
                ServerConsole.log(LogCategory.ENGINE, "ModelManager: Successfully loaded and initialized real model: $activeModelName")
            }
            val duration = System.currentTimeMillis() - startTime
            com.edge.llm.server.util.ServerStats.modelLoadTimeMs = duration
            true
        } catch (e: Exception) {
            val errorMsg = e.message ?: e.toString()
            val hint = if (useGpu) " (Tip: If GPU load fails, try disabling GPU and using CPU mode. Many Mali/Exynos/Adreno GPUs lack OpenCL compatibility with LiteRT-LM)" else ""
            ServerConsole.log(LogCategory.ENGINE, "ModelManager ERROR: Swapping failed: $errorMsg$hint")
            
            // Persist crash details for startup diagnostics screen
            val logDir = ServerConsole.logFile?.parentFile
            if (logDir != null) {
                try {
                    val crashFile = File(logDir, "crash_log.txt")
                    crashFile.writeText("ENGINE INITIALIZATION FAILURE - ${java.util.Date()}\nError: $errorMsg$hint")
                } catch (ex: Exception) {}
            }

            loadingError = errorMsg
            isModelLoaded = false
            isGpuActive = false
            
            // Fallback to active mock to prevent service crash
            try {
                val mock = MockInferenceProvider()
                mock.initialize()
                activeProvider = mock
                activeModelName = "mock-model"
                isMockMode = true
            } catch (fallbackEx: Exception) {
                // Secondary fail-safe
            }
            throw e
        } finally {
            isLoading = false
        }
    }

    /**
     * Unloads the active model completely, returns to offline state, and triggers GC to release memory.
     */
    fun unloadActiveModel() {
        ServerConsole.log(LogCategory.ENGINE, "ModelManager: Unloading model and cleaning native heap reference...")
        try {
            activeProvider.unload()
        } catch (e: Exception) {
            ServerConsole.log(LogCategory.ENGINE, "Error unloading provider: ${e.message}")
        } finally {
            // Revert to non-initialized mock
            activeProvider = MockInferenceProvider()
            activeModelName = "mock-model"
            isMockMode = true
            isModelLoaded = false
            isLoading = false
            isGpuActive = false
            loadingError = null
            
            // Clean heap
            System.gc()
            ServerConsole.log(LogCategory.ENGINE, "ModelManager: Garbage collection triggered. Memory cleaned.")
        }
    }
}
