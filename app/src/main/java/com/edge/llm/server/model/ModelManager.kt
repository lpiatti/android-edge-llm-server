package com.edge.llm.server.model

import android.app.ActivityManager
import android.content.Context
import android.os.Build
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
    val availMemGb: Double get() = availMemBytes / (1024.0 * 1024.0 * 1024.0)
    val totalMemGb: Double get() = totalMemBytes / (1024.0 * 1024.0 * 1024.0)
    val usedMemMb: Long get() = (totalMemBytes - availMemBytes) / (1024 * 1024)
    val percentUsed: Int get() = if (totalMemMb > 0) ((usedMemMb.toDouble() / totalMemMb.toDouble()) * 100).toInt() else 0

    fun formatPhysicalRam(): String {
        return "%.1f GB free / %.1f GB total (%d%% OS used)".format(availMemGb, totalMemGb, percentUsed)
    }
}

/**
 * Result metrics for cache file purge operations.
 */
data class PurgeResult(
    val filesDeleted: Int,
    val bytesFreed: Long
)

/**
 * Hardware profile capturing CPU, GPU, SoC and architecture characteristics.
 */
data class HardwareProfile(
    val manufacturer: String,
    val model: String,
    val deviceCodename: String,
    val soc: String,
    val cpuCores: Int,
    val primaryAbi: String,
    val isOpenClAvailable: Boolean,
    val isLowRamDevice: Boolean,
    val androidVersion: String
) {
    fun summaryLine(): String {
        val openClTag = if (isOpenClAvailable) "OpenCL Available" else "No OpenCL"
        return "SoC: $soc | CPU: $cpuCores Cores ($primaryAbi) | GPU: $openClTag"
    }

    fun fullDeviceLine(): String {
        return "$manufacturer $model ($deviceCodename) - $androidVersion"
    }
}

/**
 * Feasibility classification levels for loading heavy LLMs.
 */
enum class FeasibilityLevel {
    SAFE,
    TIGHT,
    CRITICAL_OOM_RISK
}

/**
 * Audit outcome evaluating whether selected model fits in available memory.
 */
data class FeasibilityAudit(
    val modelFileName: String,
    val modelSizeBytes: Long,
    val modelSizeMb: Long,
    val estimatedPeakAllocationMb: Long,
    val currentAvailRamMb: Long,
    val totalRamMb: Long,
    val level: FeasibilityLevel,
    val recommendation: String
)

/**
 * Result of aggressive memory sweep.
 */
data class DeepSweepResult(
    val appsTargeted: Int,
    val cacheFilesDeleted: Int,
    val cacheBytesFreed: Long,
    val jvmBytesFreed: Long,
    val availRamBeforeMb: Long,
    val availRamAfterMb: Long
) {
    val netRamFreedMb: Long get() = maxOf(0L, availRamAfterMb - availRamBeforeMb)
}

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
     * Resolves hardware specs useful for edge LLM inference (SoC, CPU cores, ABI, GPU OpenCL driver).
     */
    fun getHardwareProfile(context: Context): HardwareProfile {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val s = Build.SOC_MODEL
                if (!s.isNullOrBlank() && s != Build.UNKNOWN) s else Build.HARDWARE
            } catch (e: Throwable) {
                Build.HARDWARE
            }
        } else {
            Build.HARDWARE
        }

        val openClPaths = listOf(
            "/vendor/lib64/libOpenCL.so",
            "/system/vendor/lib64/libOpenCL.so",
            "/system/lib64/libOpenCL.so",
            "/vendor/lib/libOpenCL.so",
            "/system/lib/libOpenCL.so"
        )
        val hasOpenCl = openClPaths.any { File(it).exists() }

        return HardwareProfile(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            deviceCodename = Build.DEVICE,
            soc = soc,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a",
            isOpenClAvailable = hasOpenCl,
            isLowRamDevice = am.isLowRamDevice,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        )
    }

    /**
     * Audits whether the selected model can safely fit into available RAM, factoring in
     * weights, OpenCL shader compilation buffers, and KV-cache context allocations.
     */
    fun auditModelFeasibility(context: Context, modelFile: File?): FeasibilityAudit {
        val mem = getSystemMemoryInfo(context)
        if (modelFile == null || !modelFile.exists()) {
            return FeasibilityAudit(
                modelFileName = "No Model Selected",
                modelSizeBytes = 0L,
                modelSizeMb = 0L,
                estimatedPeakAllocationMb = 0L,
                currentAvailRamMb = mem.availMemMb,
                totalRamMb = mem.totalMemMb,
                level = FeasibilityLevel.SAFE,
                recommendation = "Select a .litertlm model file to evaluate memory feasibility."
            )
        }

        val sizeBytes = modelFile.length()
        val sizeMb = sizeBytes / (1024 * 1024)
        // Peak memory estimate: Weights + GPU compilation scratch (~1.2GB) + KV cache (~0.6GB)
        val estimatedPeakMb = (sizeMb * 1.25).toLong() + 800L

        val (level, rec) = when {
            mem.availMemMb >= estimatedPeakMb + 400L -> {
                FeasibilityLevel.SAFE to "Physical RAM is sufficient for standard GPU initialization."
            }
            mem.availMemMb >= sizeMb + 400L -> {
                FeasibilityLevel.TIGHT to "RAM is tight. Enable Samsung RAM Plus (6-8GB) and limit background processes to prevent LMK crash."
            }
            else -> {
                FeasibilityLevel.CRITICAL_OOM_RISK to "HIGH OOM RISK: Peak allocation (~${estimatedPeakMb}MB) exceeds available RAM (${mem.availMemMb}MB). Must run Deep Sweep, configure 8GB RAM Plus, or test in CPU mode ([ GPU: OFF ])."
            }
        }

        return FeasibilityAudit(
            modelFileName = modelFile.name,
            modelSizeBytes = sizeBytes,
            modelSizeMb = sizeMb,
            estimatedPeakAllocationMb = estimatedPeakMb,
            currentAvailRamMb = mem.availMemMb,
            totalRamMb = mem.totalMemMb,
            level = level,
            recommendation = rec
        )
    }

    /**
     * Performs an aggressive memory sweep to maximize contiguous physical RAM before loading large LLMs:
     * 1. Terminates cached 3rd-party background app processes.
     * 2. Purges temporary compilation and cache files.
     * 3. Executes JVM GC and object finalization.
     */
    fun performDeepRamSweep(context: Context): DeepSweepResult {
        ServerConsole.log(LogCategory.ENGINE, "ModelManager: Starting Deep RAM Sweep...")
        val memBefore = getSystemMemoryInfo(context)
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        var appsTargeted = 0

        try {
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(0)
            val myPkg = context.packageName
            installedApps.forEach { appInfo ->
                val pkg = appInfo.packageName
                if (pkg != myPkg && !pkg.startsWith("android") && !pkg.startsWith("com.android.systemui")) {
                    try {
                        am.killBackgroundProcesses(pkg)
                        appsTargeted++
                    } catch (e: Exception) {
                        // Ignore individual package failures
                    }
                }
            }
            ServerConsole.log(LogCategory.ENGINE, "ModelManager: Swept cached background processes for $appsTargeted packages.")
        } catch (e: Exception) {
            ServerConsole.log(LogCategory.ENGINE, "ModelManager: Background sweep warning: ${e.message}")
        }

        val purgeRes = purgeCacheFiles(context)
        val jvmFreed = trimMemoryAndCollectGarbage()
        val memAfter = getSystemMemoryInfo(context)
        val netFreed = maxOf(0L, memAfter.availMemMb - memBefore.availMemMb)
        ServerConsole.log(LogCategory.ENGINE, "ModelManager: Deep Sweep completed. Net RAM freed: $netFreed MB (Available: ${memAfter.availMemMb} MB).")

        return DeepSweepResult(
            appsTargeted = appsTargeted,
            cacheFilesDeleted = purgeRes.filesDeleted,
            cacheBytesFreed = purgeRes.bytesFreed,
            jvmBytesFreed = jvmFreed,
            availRamBeforeMb = memBefore.availMemMb,
            availRamAfterMb = memAfter.availMemMb
        )
    }

    /**
     * Trims JVM heap and requests the runtime to finalize non-referenced objects.
     * Returns the approximate number of bytes reclaimed from the JVM heap.
     */
    fun trimMemoryAndCollectGarbage(): Long {
        ServerConsole.log(LogCategory.ENGINE, "ModelManager: Running GC and memory finalization...")
        val rt = Runtime.getRuntime()
        val heapBefore = rt.totalMemory() - rt.freeMemory()
        System.gc()
        rt.runFinalization()
        System.gc()
        val heapAfter = rt.totalMemory() - rt.freeMemory()
        val freedBytes = maxOf(0L, heapBefore - heapAfter)
        ServerConsole.log(LogCategory.ENGINE, "ModelManager: Memory trim completed. Reclaimed ${freedBytes / 1024} KB of JVM heap.")
        return freedBytes
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
     * In the dedicated models directory, all non-.litertlm files (such as .bin, .tmp, .cache)
     * are deleted unconditionally, while genuine .litertlm model weight files are strictly preserved.
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

        // 2. Scan dedicated models directory (/sdcard/Download/llm-server/models/)
        // In this dedicated directory, any file that is NOT a genuine .litertlm model (e.g. .bin, .tmp, .cache)
        // is an unwanted runtime or cache artifact and MUST be purged.
        try {
            val modelsDir = getModelsDirectory()
            if (modelsDir.exists() && modelsDir.isDirectory) {
                modelsDir.listFiles()?.forEach { file ->
                    val name = file.name
                    if (file.isFile && !name.endsWith(".litertlm", ignoreCase = true)) {
                        val size = file.length()
                        if (file.delete()) {
                            count++
                            bytesFreed += size
                            ServerConsole.log(LogCategory.ENGINE, "Purged non-model artifact from models folder: $name (${size / 1024} KB)")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            ServerConsole.log(LogCategory.ENGINE, "Warning scanning models directory: ${e.message}")
        }

        // 3. Scan broader Downloads folder (/sdcard/Download/)
        // Conservative rule: only purge files that explicitly match known cache or temporary patterns
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir.exists() && downloadsDir.isDirectory) {
                downloadsDir.listFiles()?.forEach { file ->
                    val name = file.name
                    if (file.isFile && !name.endsWith(".litertlm", ignoreCase = true)) {
                        val isCacheArtifact = name.contains("_mldrift_") ||
                                name.contains("_weight_cache") ||
                                name.contains("_program_cache") ||
                                name.endsWith(".cache", ignoreCase = true) ||
                                name.startsWith("temp_") ||
                                (name.endsWith(".bin", ignoreCase = true) && (name.contains("cache") || name.contains("weight") || name.contains("mldrift")))
                        if (isCacheArtifact) {
                            val size = file.length()
                            if (file.delete()) {
                                count++
                                bytesFreed += size
                                ServerConsole.log(LogCategory.ENGINE, "Purged orphan cache artifact from Downloads: $name (${size / 1024} KB)")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            ServerConsole.log(LogCategory.ENGINE, "Warning scanning Downloads directory: ${e.message}")
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
