package com.edge.llm.server.model

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import com.edge.llm.server.util.LogCategory
import com.edge.llm.server.util.ServerConsole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Diagnostic report of system physical RAM.
 */
data class MemoryReport(
    val availMemBytes: Long,
    val totalMemBytes: Long,
    val thresholdBytes: Long,
    val isLowMemory: Boolean
) {
    val availMemMb: Long get() = availMemBytes / (1024 * 1024)
    val totalMemMb: Long get() = totalMemBytes / (1024 * 1024)
    val usedMemMb: Long get() = (totalMemBytes - availMemBytes) / (1024 * 1024)
    val percentUsed: Int get() = if (totalMemMb > 0) ((usedMemMb.toDouble() / totalMemMb.toDouble()) * 100).toInt() else 0
}

/**
 * Result metrics for cache file purge operations.
 */
data class PurgeResult(
    val filesDeleted: Int,
    val bytesFreed: Long
)

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
     * Resolves real device physical RAM metrics using ActivityManager.
     */
    fun getSystemMemoryInfo(context: Context): MemoryReport {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        return MemoryReport(
            availMemBytes = memInfo.availMem,
            totalMemBytes = memInfo.totalMem,
            thresholdBytes = memInfo.threshold,
            isLowMemory = memInfo.lowMemory
        )
    }

    /**
     * Trims JVM heap and requests the runtime to finalize non-referenced objects.
     */
    fun trimMemoryAndCollectGarbage() {
        ServerConsole.log(LogCategory.ENGINE, "ModelManager: Running GC and memory finalization...")
        System.gc()
        Runtime.getRuntime().runFinalization()
        System.gc()
        ServerConsole.log(LogCategory.ENGINE, "ModelManager: Memory trim completed.")
    }

    /**
     * Resolves the app-private cache directory dedicated to LiteRT-LM runtime compilation artifacts.
     * Ensures the directory exists before returning.
     */
    fun getPrivateCacheDirectory(context: Context): File {
        val cacheDir = File(context.cacheDir, "litertlm_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return cacheDir
    }

    /**
     * Purges temporary cache files generated during inference or GPU compilation.
     * Cleans both the private app cache directory and cleans up any orphan cache files
     * left in public storage (/sdcard/Download/llm-server/models/ and /sdcard/Download/).
     * Rigorously preserves all .litertlm model weight files.
     */
    fun purgeCacheFiles(context: Context? = null): PurgeResult {
        var count = 0
        var bytesFreed = 0L

        // 1. Purge private app cache directory
        if (context != null) {
            try {
                val privateCache = getPrivateCacheDirectory(context)
                if (privateCache.exists()) {
                    privateCache.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val size = file.length()
                            if (file.delete()) {
                                count++
                                bytesFreed += size
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                ServerConsole.log(LogCategory.ENGINE, "Warning cleaning private cache: ${e.message}")
            }
        }

        // 2. Scan public models directory and Downloads for orphan cache files (*_mldrift_*, *_weight_cache*, *_program_cache*, *.cache, temp_*)
        val dirsToInspect = listOfNotNull(
            getModelsDirectory(),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )

        for (dir in dirsToInspect) {
            if (dir.exists() && dir.isDirectory) {
                try {
                    dir.listFiles()?.forEach { file ->
                        val name = file.name
                        // Never touch genuine model weights
                        if (!name.endsWith(".litertlm") && file.isFile) {
                            val isCacheArtifact = name.contains("_mldrift_") ||
                                    name.contains("_weight_cache") ||
                                    name.contains("_program_cache") ||
                                    name.endsWith(".cache") ||
                                    name.startsWith("temp_")
                            if (isCacheArtifact) {
                                val size = file.length()
                                if (file.delete()) {
                                    count++
                                    bytesFreed += size
                                    ServerConsole.log(LogCategory.ENGINE, "Purged orphan cache artifact: $name (${size / 1024} KB)")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    ServerConsole.log(LogCategory.ENGINE, "Warning scanning directory ${dir.name}: ${e.message}")
                }
            }
        }

        val mbFreed = bytesFreed / (1024.0 * 1024.0)
        ServerConsole.log(LogCategory.ENGINE, "Cache Purge: Removed $count files (%.2f MB freed).".format(mbFreed))
        return PurgeResult(count, bytesFreed)
    }

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
        useGpu: Boolean = false,
        cacheDir: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        isLoading = true
        isGpuActive = false
        loadingError = null
        val startTime = System.currentTimeMillis()
        ServerConsole.log(LogCategory.ENGINE, "ModelManager: Swapping model state (useMock=$useMock, path=$modelPath, useGpu=$useGpu, cacheDir=${cacheDir ?: "default"})...")
        
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
                val realProvider = LiteRtLmInferenceProvider(modelPath, useGpu, cacheDir)
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
     * Unloads the active model completely, purges cache files, returns to offline state, and triggers GC.
     */
    fun unloadActiveModel(context: Context? = null) {
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
            
            // Clean cache files and trim memory
            purgeCacheFiles(context)
            trimMemoryAndCollectGarbage()
        }
    }
}
