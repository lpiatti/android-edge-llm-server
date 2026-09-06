package com.edge.llm.server.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.edge.llm.server.model.ModelManager
import com.edge.llm.server.service.LlmServerService
import com.edge.llm.server.util.LogCategory
import com.edge.llm.server.util.LogEntry
import com.edge.llm.server.util.ServerConsole
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * MainActivity: The primary control dashboard panel of our edge LLM server node.
 * Uses vanilla programmatic Kotlin layouts to support high build stability and low APK size.
 */
class MainActivity : AppCompatActivity() {

    // View States
    private var currentViewState = "MAIN" // "CRASH", "PERMISSION", "MAIN"

    // Layout Containers
    private lateinit var rootLayout: LinearLayout
    private lateinit var contentContainer: LinearLayout
    
    // Sticky Status Island
    private lateinit var statusIsland: StatusIsland

    // Tab Layout Controls
    private lateinit var tabBar: LinearLayout
    private lateinit var modelScrollView: ScrollView
    private lateinit var serverScrollView: ScrollView
    private lateinit var testScrollView: ScrollView
    private lateinit var logsScrollView: ScrollView

    private lateinit var modelTabBtn: Button
    private lateinit var serverTabBtn: Button
    private lateinit var testTabBtn: Button
    private lateinit var logsTabBtn: Button

    // Tab 1 (Model Engine) Views
    private lateinit var mockPillModel: Button
    private lateinit var realPillModel: Button
    private lateinit var cpuModelPill: Button
    private lateinit var gpuModelPill: Button
    private lateinit var backendSelectorRow: LinearLayout
    private lateinit var pickerCard: LinearLayout
    private lateinit var pickerBtn: Button
    private lateinit var modelFileLabel: TextView
    private lateinit var initModelBtn: Button
    private lateinit var stopModelBtn: Button
    
    private var selectedModelPath: String? = null
    private var selectedModelName: String = "No file selected"
    private var isMockEngineSelected = true
    private var isGpuSelected = false

    // Tab 2 (Server Daemon) Views
    private lateinit var toggleServerBtn: Button
    private lateinit var copyUrlBtn: Button
    private lateinit var batteryBtn: Button
    private lateinit var wifiPill: Button
    private lateinit var mobilePill: Button
    private lateinit var allPill: Button
    private var selectedBindingInterface = "All"

    private lateinit var serverAddressLabel: TextView
    private lateinit var statsRequestsText: TextView
    private lateinit var statsQueueText: TextView
    private lateinit var statsSpeedText: TextView
    private lateinit var statsTokensText: TextView
    private lateinit var statsLoadTimeText: TextView

    // Tab 3 (API Test Suite) Views
    private lateinit var quickShellInput: EditText
    private lateinit var quickShellSendBtn: Button
    private lateinit var quickShellStreamBtn: Button
    private var isQuickStreamEnabled = true

    private lateinit var presetS1Btn: Button
    private lateinit var presetStreamBtn: Button
    private lateinit var presetS2QueueBtn: Button
    private lateinit var presetHealthBtn: Button

    private lateinit var rawJsonToggleBtn: Button
    private lateinit var rawJsonContainer: LinearLayout
    private var isRawJsonVisible = false

    private lateinit var openAiPill: Button
    private lateinit var ollamaPill: Button
    private lateinit var epPill1: Button
    private lateinit var epPill2: Button
    private lateinit var epPill3: Button
    private lateinit var chipRow: LinearLayout
    private lateinit var payloadEditor: EditText
    private lateinit var runRawTestBtn: Button

    private lateinit var latencyMetric: TextView
    private lateinit var statusMetric: TextView
    private lateinit var sizeMetric: TextView
    private lateinit var testConsole: CollapsibleLogConsole
    private var activeProtocol = "OpenAI"
    private var selectedEndpointIndex = 0

    // Tab 4 (System Logs) Views
    private lateinit var filterAllBtn: Button
    private lateinit var filterServerBtn: Button
    private lateinit var filterEngineBtn: Button
    private lateinit var filterUiBtn: Button
    private lateinit var copyLogsBtn: Button
    private lateinit var clearLogsBtn: Button
    private lateinit var fullSystemConsole: CollapsibleLogConsole
    private var activeLogFilter = "ALL" // "ALL", "SERVER", "ENGINE", "UI"

    // SAF File Picker Launcher
    private val filePickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Ignore if not supported
                }
                
                // Show resolving progress on the button to prevent freeze visuals
                pickerBtn.isEnabled = false
                pickerBtn.text = "⏳ Resolving file path (checking local match)..."
                
                Thread {
                    val path = UriHelper.resolveUriToPath(this, uri)
                    runOnUiThread {
                        pickerBtn.isEnabled = true
                        pickerBtn.text = "📂 Select Model File (.litertlm)"
                        if (path != null) {
                            selectedModelPath = path
                            selectedModelName = File(path).name
                            ServerConsole.log(LogCategory.UI, "Selected model: $selectedModelName (path: $path)")
                            
                            // Save selected model path and name to SharedPreferences
                            getSharedPreferences("llm_server_prefs", Context.MODE_PRIVATE)
                                .edit()
                                .putString("selected_model_path", path)
                                .putString("selected_model_name", selectedModelName)
                                .apply()
                                
                            updateModelDisplay()
                        } else {
                            ServerConsole.log(LogCategory.UI, "Failed to resolve local path from picker.")
                        }
                    }
                }.start()
            }
        }
    }

    // Timer logic during test suite
    private val testTimerHandler = Handler(Looper.getMainLooper())
    private var testStartTime: Long = 0
    private var isTestingApi = false
    private var activeTestButton: Button? = null
    private var defaultTestButtonText: String = ""
    private val testTimerRunnable = object : Runnable {
        override fun run() {
            if (isTestingApi) {
                val elapsedSec = (System.currentTimeMillis() - testStartTime) / 1000.0
                activeTestButton?.text = "Testing... %.1fs".format(elapsedSec)
                testTimerHandler.postDelayed(this, 100)
            }
        }
    }

    private val statusUpdateHandler = Handler(Looper.getMainLooper())
    private val statusUpdateRunnable = object : Runnable {
        override fun run() {
            if (currentViewState == "MAIN") {
                updateStickyStatus()
                updateModelUiState()
                updateServerUiState()
                updateServerTelemetry()

                // Dynamically maintain clean_shutdown based on whether daemon or model is active
                val active = LlmServerService.isServiceRunning
                val loaded = ModelManager.isModelLoaded
                val loading = ModelManager.isLoading
                val isDirtyState = active || loaded || loading
                getSharedPreferences("llm_server_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("clean_shutdown", !isDirtyState)
                    .apply()
            }
            statusUpdateHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize persistent logs file in ServerConsole
        ServerConsole.logFile = File(filesDir, "persistent_logs.txt")

        // Uncaught exceptions catcher
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val file = File(filesDir, "crash_log.txt")
                java.io.FileOutputStream(file).use { fos ->
                    java.io.PrintStream(fos).use { ps ->
                        ps.println("CRASH REPORT - ${java.util.Date()}")
                        ps.println("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
                        throwable.printStackTrace(ps)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Base container layout
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(24, 24, 24, 24)
        }
        setContentView(rootLayout)

        // Evaluate crash diagnostics threshold
        val crashFile = File(filesDir, "crash_log.txt")
        
        // Check SharedPreferences for dirty shutdown (native crash / LMK)
        val prefs = getSharedPreferences("llm_server_prefs", Context.MODE_PRIVATE)
        val cleanShutdown = prefs.getBoolean("clean_shutdown", true)
        
        if (!cleanShutdown && !crashFile.exists()) {
            try {
                java.io.FileOutputStream(crashFile).use { fos ->
                    java.io.PrintStream(fos).use { ps ->
                        ps.println("⚠️ SYSTEM DIAGNOSTICS - POTENTIAL NATIVE CRASH OR LMK")
                        ps.println("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
                        ps.println("Timestamp: ${java.util.Date()}")
                        ps.println("\nDiagnostic Heuristics:")
                        ps.println("The application terminated unexpectedly without a clean shutdown.")
                        ps.println("This is highly indicative of a native C++ crash (e.g. LiteRT segment fault) or")
                        ps.println("a Low Memory Killer (LMK) event where the OS killed the process to reclaim RAM.")
                        ps.println("\nRecommendations:")
                        ps.println("1. If using GPU backend on a device with a Mali GPU (e.g., Exynos), disable GPU and use CPU.")
                        ps.println("2. Make sure the model file is quantized (4-bit) and under 1.8GB for 6GB/8GB RAM devices.")
                        ps.println("3. Ensure that other heavy background applications are closed.")
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        if (crashFile.exists()) {
            val elapsed = System.currentTimeMillis() - crashFile.lastModified()
            val twoHours = 2 * 60 * 60 * 1000
            if (elapsed < twoHours) {
                val crashText = crashFile.readText()
                setupCrashDiagnosticsView(crashText)
                return
            } else {
                crashFile.delete()
            }
        }

        // Mark current session as running (dirty until stopped cleanly)
        prefs.edit().putBoolean("clean_shutdown", false).apply()

        checkPermissionsAndProceed()
    }

    override fun onResume() {
        super.onResume()
        statusUpdateHandler.post(statusUpdateRunnable)
        if (currentViewState == "PERMISSION") {
            if (hasNotificationPermission() && hasStoragePermission()) {
                checkPermissionsAndProceed()
            } else {
                setupPermissionOnboardingView()
            }
        }
        if (currentViewState == "MAIN") {
            updateStickyStatus()
            refreshIpAddress()
            logAllInterfaces()
        }
    }

    override fun onPause() {
        super.onPause()
        statusUpdateHandler.removeCallbacks(statusUpdateRunnable)
    }

    private fun checkPermissionsAndProceed() {
        if (hasNotificationPermission() && hasStoragePermission()) {
            setupMainDashboardView()
        } else {
            setupPermissionOnboardingView()
        }
    }

    // --- VIEW 1: CRASH DIAGNOSTICS VIEW ---

    private fun setupCrashDiagnosticsView(crashText: String) {
        currentViewState = "CRASH"
        rootLayout.removeAllViews()

        val titleView = TextView(this).apply {
            text = "⚠️ System Diagnostics"
            textSize = 20f
            setTextColor(Color.parseColor("#FF4444"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }
        rootLayout.addView(titleView)

        val descView = TextView(this).apply {
            text = "The application crashed during the previous run. Auto-start controls and active background loads have been isolated to prevent boot loops."
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 0, 0, 24)
            gravity = Gravity.CENTER
        }
        rootLayout.addView(descView)

        val logScroller = ScrollView(this).apply {
            val tv = TextView(context).apply {
                text = crashText
                textSize = 10f
                setTextColor(Color.parseColor("#00FF66"))
                setBackgroundColor(Color.parseColor("#0A0A0A"))
                setPadding(24, 24, 24, 24)
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
            }
            addView(tv)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f)
            layoutParams = params
        }
        rootLayout.addView(logScroller)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 8)
            gravity = Gravity.CENTER
        }

        val copyBtn = Button(this).apply {
            text = "Copy Stacktrace"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#444444"))
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Crash trace", crashText)
                clipboard.setPrimaryClip(clip)
                text = "Copied!"
            }
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 16, 0)
            }
            layoutParams = params
        }
        actionRow.addView(copyBtn)

        val dismissBtn = Button(this).apply {
            text = "Dismiss & Open"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0099CC"))
            setOnClickListener {
                File(filesDir, "crash_log.txt").delete()
                getSharedPreferences("llm_server_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("clean_shutdown", true)
                    .apply()
                checkPermissionsAndProceed()
            }
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
        }
        actionRow.addView(dismissBtn)

        rootLayout.addView(actionRow)
    }

    // --- VIEW 2: PERMISSION ONBOARDING VIEW ---

    private fun setupPermissionOnboardingView() {
        currentViewState = "PERMISSION"
        rootLayout.removeAllViews()

        val titleView = TextView(this).apply {
            text = "Server Setup Permissions"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 16)
        }
        rootLayout.addView(titleView)

        val descView = TextView(this).apply {
            text = "To serve edge inference stably in the background (24/7 on AC power), the daemon requires storage access to load models and notification permissions to hold execution locks."
            textSize = 13f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 0, 0, 32)
            gravity = Gravity.CENTER
        }
        rootLayout.addView(descView)

        // Card 1: Notification Permission
        val hasNotif = hasNotificationPermission()
        val card1 = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.parseColor("#1C1C1C"))
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 16)
            }
            layoutParams = params
        }
        val cardTitle1 = TextView(this).apply {
            text = if (hasNotif) "🟢 Notification Alerts: GRANTED" else "🔴 Notification Alerts: REQUIRED"
            textSize = 14f
            setTextColor(if (hasNotif) Color.parseColor("#00FF66") else Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        card1.addView(cardTitle1)
        val cardDesc1 = TextView(this).apply {
            text = "Required on Android 13+ to post persistent service status cards in the system drawer."
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 4, 0, 12)
        }
        card1.addView(cardDesc1)
        val btn1 = Button(this).apply {
            text = if (hasNotif) "Permission Granted" else "Enable Notifications"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3366BB"))
            setOnClickListener { requestNotificationPermission() }
            setVisualEnabled(!hasNotif)
        }
        card1.addView(btn1)
        rootLayout.addView(card1)

        // Card 2: Storage Permission
        val hasStore = hasStoragePermission()
        val card2 = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.parseColor("#1C1C1C"))
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 24)
            }
            layoutParams = params
        }
        val cardTitle2 = TextView(this).apply {
            text = if (hasStore) "🟢 Storage & Model Access: GRANTED" else "🔴 Storage & Model Access: REQUIRED"
            textSize = 14f
            setTextColor(if (hasStore) Color.parseColor("#00FF66") else Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        card2.addView(cardTitle2)
        val cardDesc2 = TextView(this).apply {
            text = "Required to allow loading of large .litertlm models directly from external files without duplicate copies."
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 4, 0, 12)
        }
        card2.addView(cardDesc2)
        val btn2 = Button(this).apply {
            text = if (hasStore) "Permission Granted" else "Grant Storage Access"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF8800"))
            setOnClickListener { requestStoragePermission() }
            setVisualEnabled(!hasStore)
        }
        card2.addView(btn2)
        rootLayout.addView(card2)
    }

    // --- VIEW 3: MAIN DASHBOARD VIEW ---

    private fun setupMainDashboardView() {
        currentViewState = "MAIN"
        rootLayout.removeAllViews()

        // 1. Top Sticky Status Island
        statusIsland = StatusIsland(this)
        rootLayout.addView(statusIsland)

        // 2. Center Content View
        contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f)
            layoutParams = params
        }
        rootLayout.addView(contentContainer)

        // Initialize Tab ScrollView wrapper elements
        createModelTab()
        createServerTab()
        createTestTab()
        createLogsTab()

        // Add ScrollViews to container
        contentContainer.addView(modelScrollView)
        contentContainer.addView(serverScrollView)
        contentContainer.addView(testScrollView)
        contentContainer.addView(logsScrollView)

        // 3. Bottom Tab Bar
        tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            setPadding(8, 12, 8, 12)
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutParams = params
        }

        val btnParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
            setMargins(0, 0, 4, 0)
        }

        modelTabBtn = Button(this).apply {
            text = "[ ENGINE ]"
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setOnClickListener { switchTab(0) }
        }
        tabBar.addView(modelTabBtn, btnParams)

        serverTabBtn = Button(this).apply {
            text = "[ DAEMON ]"
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setOnClickListener { switchTab(1) }
        }
        tabBar.addView(serverTabBtn, btnParams)

        testTabBtn = Button(this).apply {
            text = "[ TESTER ]"
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setOnClickListener { switchTab(2) }
        }
        tabBar.addView(testTabBtn, btnParams)

        logsTabBtn = Button(this).apply {
            text = "[ LOGS ]"
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setOnClickListener { switchTab(3) }
        }
        val logsBtnParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        tabBar.addView(logsTabBtn, logsBtnParams)

        rootLayout.addView(tabBar)

        // Hook console log updates
        ServerConsole.logListener = { entry ->
            runOnUiThread {
                if (entry.message == "CLEAR_LOGS") {
                    fullSystemConsole.clear()
                } else {
                    refreshSystemLogsView()
                }
            }
        }

        loadPersistedSettings()
        switchTab(0)
        refreshConsoleLogs()
        refreshPayloadEditor()
    }

    private fun loadPersistedSettings() {
        val prefs = getSharedPreferences("llm_server_prefs", Context.MODE_PRIVATE)
        
        // 1. Mock vs Real Engine Selection
        val isMock = prefs.getBoolean("is_mock_engine", true)
        // Bypass return guard by setting inverse first
        isMockEngineSelected = !isMock
        setModelMockMode(isMock)
        
        // 2. GPU vs CPU Backend Selection
        val isGpu = prefs.getBoolean("is_gpu_backend", false)
        isGpuSelected = !isGpu
        setHardwareBackend(isGpu)
        
        // 3. Last Selected Model Path
        val modelPath = prefs.getString("selected_model_path", null)
        val modelName = prefs.getString("selected_model_name", "No file selected") ?: "No file selected"
        if (modelPath != null && File(modelPath).exists()) {
            selectedModelPath = modelPath
            selectedModelName = modelName
            updateModelDisplay()
        }
        
        // 4. Last Selected Binding Interface
        val bindingInterface = prefs.getString("selected_interface", "All") ?: "All"
        selectedBindingInterface = "" // Force update
        setInterfaceBinding(bindingInterface)
    }

    private fun switchTab(index: Int) {
        modelScrollView.visibility = View.GONE
        serverScrollView.visibility = View.GONE
        testScrollView.visibility = View.GONE
        logsScrollView.visibility = View.GONE

        modelTabBtn.setBackgroundColor(Color.parseColor("#121212"))
        modelTabBtn.setTextColor(Color.parseColor("#888888"))
        serverTabBtn.setBackgroundColor(Color.parseColor("#121212"))
        serverTabBtn.setTextColor(Color.parseColor("#888888"))
        testTabBtn.setBackgroundColor(Color.parseColor("#121212"))
        testTabBtn.setTextColor(Color.parseColor("#888888"))
        logsTabBtn.setBackgroundColor(Color.parseColor("#121212"))
        logsTabBtn.setTextColor(Color.parseColor("#888888"))

        when (index) {
            0 -> {
                modelScrollView.visibility = View.VISIBLE
                modelTabBtn.setBackgroundColor(Color.parseColor("#3366BB"))
                modelTabBtn.setTextColor(Color.WHITE)
            }
            1 -> {
                serverScrollView.visibility = View.VISIBLE
                serverTabBtn.setBackgroundColor(Color.parseColor("#3366BB"))
                serverTabBtn.setTextColor(Color.WHITE)
                refreshIpAddress()
            }
            2 -> {
                testScrollView.visibility = View.VISIBLE
                testTabBtn.setBackgroundColor(Color.parseColor("#3366BB"))
                testTabBtn.setTextColor(Color.WHITE)
            }
            3 -> {
                logsScrollView.visibility = View.VISIBLE
                logsTabBtn.setBackgroundColor(Color.parseColor("#3366BB"))
                logsTabBtn.setTextColor(Color.WHITE)
                refreshSystemLogsView()
            }
        }
    }

    private fun updateStickyStatus() {
        if (!::statusIsland.isInitialized) return
        val activeHost = LlmServerService.activeBindHost
        val displayHost = if (activeHost == "0.0.0.0") getLocalIpAddress() else activeHost
        statusIsland.update(
            serverRunning = LlmServerService.isServiceRunning,
            bindHost = displayHost,
            modelLoaded = ModelManager.isModelLoaded,
            isMock = ModelManager.isMockMode,
            modelName = ModelManager.activeModelName,
            modelLoading = ModelManager.isLoading,
            isGpu = ModelManager.isGpuActive
        )
    }

    private fun updateServerTelemetry() {
        if (!::statsRequestsText.isInitialized) return
        val stats = com.edge.llm.server.util.ServerStats
        statsRequestsText.text = "${stats.totalRequests} (Active: ${stats.activeConnections})"

        if (::statsQueueText.isInitialized) {
            val qCount = stats.queuedRequests
            statsQueueText.text = "$qCount / 4 Slots"
            statsQueueText.setTextColor(
                when {
                    qCount >= 4 -> Color.parseColor("#FF4444")
                    qCount > 0 -> Color.parseColor("#FF8800")
                    else -> Color.parseColor("#00FF66")
                }
            )
        }

        statsSpeedText.text = if (stats.lastGenerationSpeedTps > 0) "%.1f tok/s".format(stats.lastGenerationSpeedTps) else "0.0 tok/s"
        statsTokensText.text = "${stats.totalTokensGenerated} tokens"
        statsLoadTimeText.text = if (stats.modelLoadTimeMs > 0) "%.2f s".format(stats.modelLoadTimeMs / 1000.0) else "N/A"
    }

    // --- TAB 1: MODEL ENGINE LAYOUT (Wrapped inside ScrollView) ---

    private fun createModelTab() {
        val modelContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Wrapping inside ScrollView
        modelScrollView = ScrollView(this).apply {
            visibility = View.GONE
            addView(modelContainer)
        }

        val sectionTitle = TextView(this).apply {
            text = "[ SYSTEM // MODEL ENGINE SETUP ]"
            textSize = 14f
            setTextColor(Color.parseColor("#00FF66"))
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 16)
        }
        modelContainer.addView(sectionTitle)

        // Engine Selectors
        val engineModeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 12)
        }

        mockPillModel = Button(this).apply {
            text = "[ MOCK SIMULATOR ]"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3366BB"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = params
            setOnClickListener { setModelMockMode(true) }
        }
        engineModeRow.addView(mockPillModel)

        realPillModel = Button(this).apply {
            text = "[ REAL .LITERTLM ]"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
            setOnClickListener { setModelMockMode(false) }
        }
        engineModeRow.addView(realPillModel)
        modelContainer.addView(engineModeRow)

        // GPU vs CPU Selector Toggle pills
        backendSelectorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
            visibility = View.GONE
        }

        cpuModelPill = Button(this).apply {
            text = "[ CPU: STABLE ]"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3366BB"))
            textSize = 9f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = params
            setOnClickListener { setHardwareBackend(false) }
        }
        backendSelectorRow.addView(cpuModelPill)

        gpuModelPill = Button(this).apply {
            text = "[ GPU: EXPERIMENTAL ]"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 9f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
            setOnClickListener { setHardwareBackend(true) }
        }
        backendSelectorRow.addView(gpuModelPill)
        modelContainer.addView(backendSelectorRow)

        // Native SAF file picker card
        pickerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#161616"))
                cornerRadius = 8f
                setStroke(1, Color.parseColor("#2A2A2A"))
            }
            background = bg
            visibility = View.GONE
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 16)
            }
            layoutParams = params
        }

        modelFileLabel = TextView(this).apply {
            text = "FILE: No file selected"
            textSize = 10f
            setTextColor(Color.parseColor("#888888"))
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 10)
        }
        pickerCard.addView(modelFileLabel)

        pickerBtn = Button(this).apply {
            text = "[ 📂 SELECT MODEL FILE (.litertlm) ]"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#333333"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setOnClickListener { openModelFilePicker() }
        }
        pickerCard.addView(pickerBtn)
        modelContainer.addView(pickerCard)

        // Action Buttons Row
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        initModelBtn = Button(this).apply {
            text = "[ ▶ INITIALIZE ENGINE ]"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#22AA55"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = params
            setOnClickListener { initializeEngine() }
        }
        actionsRow.addView(initModelBtn)

        stopModelBtn = Button(this).apply {
            text = "[ ⏹ UNLOAD ENGINE ]"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC0000"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
            setOnClickListener { unloadEngine() }
        }
        actionsRow.addView(stopModelBtn)
        modelContainer.addView(actionsRow)

        val engineNote = TextView(this).apply {
            text = "> Terminal Note: Model execution runs in the background. Full API testing, direct chat prompts, and queue benchmarks are in the [TESTER] tab."
            textSize = 10f
            setTextColor(Color.parseColor("#666666"))
            typeface = Typeface.MONOSPACE
            setPadding(4, 4, 4, 4)
        }
        modelContainer.addView(engineNote)
    }

    private fun setModelMockMode(isMock: Boolean) {
        if (isMockEngineSelected == isMock) return
        isMockEngineSelected = isMock

        getSharedPreferences("llm_server_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_mock_engine", isMock)
            .apply()

        mockPillModel.setBackgroundColor(Color.parseColor(if (isMock) "#3366BB" else "#222222"))
        mockPillModel.setTextColor(Color.parseColor(if (isMock) "#FFFFFF" else "#888888"))
        realPillModel.setBackgroundColor(Color.parseColor(if (!isMock) "#3366BB" else "#222222"))
        realPillModel.setTextColor(Color.parseColor(if (!isMock) "#FFFFFF" else "#888888"))

        backendSelectorRow.visibility = if (isMock) View.GONE else View.VISIBLE
        pickerCard.visibility = if (isMock) View.GONE else View.VISIBLE
    }

    private fun setHardwareBackend(isGpu: Boolean) {
        if (isGpuSelected == isGpu) return
        isGpuSelected = isGpu

        getSharedPreferences("llm_server_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_gpu_backend", isGpu)
            .apply()

        cpuModelPill.setBackgroundColor(Color.parseColor(if (!isGpu) "#3366BB" else "#222222"))
        cpuModelPill.setTextColor(Color.parseColor(if (!isGpu) "#FFFFFF" else "#888888"))
        gpuModelPill.setBackgroundColor(Color.parseColor(if (isGpu) "#3366BB" else "#222222"))
        gpuModelPill.setTextColor(Color.parseColor(if (isGpu) "#FFFFFF" else "#888888"))
    }

    private fun initializeEngine() {
        val isMock = isMockEngineSelected
        val isGpu = isGpuSelected
        var path: String? = null
        if (!isMock) {
            path = selectedModelPath
            if (path == null) {
                ServerConsole.log(LogCategory.UI, "Engine Setup: Failed to initialize. No model selected.")
                return
            }
        }

        ServerConsole.log(LogCategory.UI, "Engine Setup: Commencing load sequence (isMock=$isMock, GPU=$isGpu)...")
        lockUiForModelLoading(true)

        Thread {
            try {
                kotlinx.coroutines.runBlocking {
                    ModelManager.loadModel(path, isMock, isGpu)
                }
                runOnUiThread {
                    ServerConsole.log(LogCategory.UI, "Engine Setup: Model loaded successfully.")
                    lockUiForModelLoading(false)
                }
            } catch (e: Exception) {
                val error = e.message ?: e.toString()
                runOnUiThread {
                    ServerConsole.log(LogCategory.UI, "Engine Setup Error: $error")
                    lockUiForModelLoading(false)
                }
            }
        }.start()
    }

    private fun unloadEngine() {
        lockUiForModelLoading(true)
        Thread {
            ModelManager.unloadActiveModel()
            runOnUiThread {
                ServerConsole.log(LogCategory.UI, "Engine Setup: Model unloaded.")
                lockUiForModelLoading(false)
            }
        }.start()
    }

    private fun lockUiForModelLoading(loading: Boolean) {
        if (loading) {
            initModelBtn.setVisualEnabled(false)
            stopModelBtn.setVisualEnabled(false)
            mockPillModel.setVisualEnabled(false)
            realPillModel.setVisualEnabled(false)
            cpuModelPill.setVisualEnabled(false)
            gpuModelPill.setVisualEnabled(false)
            pickerBtn.setVisualEnabled(false)
            
            modelTabBtn.setVisualEnabled(false)
            serverTabBtn.setVisualEnabled(false)
            testTabBtn.setVisualEnabled(false)
            logsTabBtn.setVisualEnabled(false)
        } else {
            updateModelUiState()
        }
    }

    private fun updateModelUiState() {
        val loaded = ModelManager.isModelLoaded
        val loading = ModelManager.isLoading
        val serverActive = LlmServerService.isServiceRunning

        val canConfig = !loaded && !loading && !serverActive

        initModelBtn.setVisualEnabled(canConfig)
        stopModelBtn.setVisualEnabled(loaded && !loading && !serverActive)

        mockPillModel.setVisualEnabled(canConfig)
        realPillModel.setVisualEnabled(canConfig)
        cpuModelPill.setVisualEnabled(canConfig)
        gpuModelPill.setVisualEnabled(canConfig)
        pickerBtn.setVisualEnabled(canConfig)

        modelTabBtn.setVisualEnabled(!loading)
        serverTabBtn.setVisualEnabled(!loading)
        testTabBtn.setVisualEnabled(!loading)
        logsTabBtn.setVisualEnabled(!loading)
    }

    // --- TAB 2: SERVER DAEMON LAYOUT (Wrapped inside ScrollView) ---

    private fun createServerTab() {
        val serverContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        serverScrollView = ScrollView(this).apply {
            visibility = View.GONE
            addView(serverContainer)
        }

        val sectionTitle = TextView(this).apply {
            text = "[ NETWORK // KTOR SERVER DAEMON ]"
            textSize = 14f
            setTextColor(Color.parseColor("#00FF66"))
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 16)
        }
        serverContainer.addView(sectionTitle)

        // Hero Address Panel
        serverAddressLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#FF8800"))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
            text = "● SERVER OFFLINE\nWILL BIND TO: http://127.0.0.1:8080"
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#161616"))
                cornerRadius = 8f
                setStroke(1, Color.parseColor("#2A2A2A"))
            }
            background = bg
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 12)
            }
            layoutParams = params
        }
        serverContainer.addView(serverAddressLabel)

        // Server Control Row (Start/Stop + Copy URL)
        val serverControlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        toggleServerBtn = Button(this).apply {
            text = "[ ▶ START HTTP SERVER ]"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3366BB"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = params
            setOnClickListener { toggleServerDaemon() }
        }
        serverControlRow.addView(toggleServerBtn)

        copyUrlBtn = Button(this).apply {
            text = "[ 📋 COPY URL ]"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#333333"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
            setOnClickListener {
                val host = if (LlmServerService.activeBindHost == "0.0.0.0") getLocalIpAddress() else LlmServerService.activeBindHost
                val url = "http://$host:8080"
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Server URL", url)
                clipboard.setPrimaryClip(clip)
                ServerConsole.log(LogCategory.UI, "Copied server URL to clipboard: $url")
            }
        }
        serverControlRow.addView(copyUrlBtn)
        serverContainer.addView(serverControlRow)

        // Network Interface Selector
        val bindLabel = TextView(this).apply {
            text = "BIND INTERFACE:"
            textSize = 10f
            setTextColor(Color.parseColor("#888888"))
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 6)
        }
        serverContainer.addView(bindLabel)

        val bindRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        allPill = Button(this).apply {
            text = "[ ALL (0.0.0.0) ]"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3366BB"))
            textSize = 9f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 6, 0)
            }
            layoutParams = params
            setOnClickListener { setInterfaceBinding("All") }
        }
        bindRow.addView(allPill)

        wifiPill = Button(this).apply {
            text = "[ WI-FI ]"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 9f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 6, 0)
            }
            layoutParams = params
            setOnClickListener { setInterfaceBinding("Wi-Fi") }
        }
        bindRow.addView(wifiPill)

        mobilePill = Button(this).apply {
            text = "[ CELLULAR ]"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 9f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
            setOnClickListener { setInterfaceBinding("Mobile") }
        }
        bindRow.addView(mobilePill)
        serverContainer.addView(bindRow)

        // Live Daemon Telemetry Card
        val statsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#161616"))
                cornerRadius = 8f
                setStroke(1, Color.parseColor("#2A2A2A"))
            }
            background = bg
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 16)
            }
            layoutParams = params
        }

        val statsTitle = TextView(this).apply {
            text = "[ LIVE DAEMON TELEMETRY ]"
            textSize = 11f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 8)
        }
        statsCard.addView(statsTitle)

        val createStatsRow = { labelStr: String, valRef: (TextView) -> Unit ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 3, 0, 3)
            }
            val lbl = TextView(this).apply {
                text = labelStr
                textSize = 10f
                setTextColor(Color.parseColor("#888888"))
                typeface = Typeface.MONOSPACE
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                layoutParams = params
            }
            val v = TextView(this).apply {
                text = "0"
                textSize = 10f
                setTextColor(Color.parseColor("#00FF66"))
                typeface = Typeface.MONOSPACE
            }
            valRef(v)
            row.addView(lbl)
            row.addView(v)
            row
        }

        statsCard.addView(createStatsRow("Processed Requests (Active):") { statsRequestsText = it })
        statsCard.addView(createStatsRow("Request Queue (S2):") { statsQueueText = it })
        statsCard.addView(createStatsRow("Tokens Generation Speed:") { statsSpeedText = it })
        statsCard.addView(createStatsRow("Total Tokens Generated:") { statsTokensText = it })
        statsCard.addView(createStatsRow("Model Engine Load Duration:") { statsLoadTimeText = it })

        serverContainer.addView(statsCard)

        batteryBtn = Button(this).apply {
            text = "[ ⚡ BYPASS CPU STANDBY (BATTERY OPTIMIZATION) ]"
            setTextColor(Color.parseColor("#CCCCCC"))
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 16)
            }
            layoutParams = params
            setOnClickListener { launchBatterySettings() }
        }
        serverContainer.addView(batteryBtn)
    }

    private fun toggleServerDaemon() {
        if (!ModelManager.isModelLoaded) {
            ServerConsole.log(LogCategory.UI, "Daemon Start Blocked: Engine not initialized.")
            return
        }

        val intent = Intent(this, LlmServerService::class.java)
        if (LlmServerService.isServiceRunning) {
            ServerConsole.log(LogCategory.UI, "Requesting daemon stop...")
            stopService(intent)
        } else {
            val bindHost = when (selectedBindingInterface) {
                "Wi-Fi" -> getInterfaceIp("Wi-Fi")
                "Mobile" -> getInterfaceIp("Mobile")
                else -> "0.0.0.0"
            }
            intent.putExtra("EXTRA_BIND_HOST", bindHost)
            ServerConsole.log(LogCategory.UI, "Requesting daemon start on $bindHost...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    private fun updateServerUiState() {
        val active = LlmServerService.isServiceRunning
        val modelLoaded = ModelManager.isModelLoaded
        val modelLoading = ModelManager.isLoading

        if (active) {
            toggleServerBtn.text = "[ ⏹ STOP HTTP SERVER ]"
            toggleServerBtn.setBackgroundColor(Color.parseColor("#CC0000"))
            
            wifiPill.setVisualEnabled(false)
            mobilePill.setVisualEnabled(false)
            allPill.setVisualEnabled(false)

            val activeHost = if (LlmServerService.activeBindHost == "0.0.0.0") getLocalIpAddress() else LlmServerService.activeBindHost
            serverAddressLabel.text = "🟢 SERVER ACTIVE\nURL: http://$activeHost:8080"
            serverAddressLabel.setTextColor(Color.parseColor("#00FF66"))
            copyUrlBtn.setVisualEnabled(true)
        } else {
            toggleServerBtn.text = "[ ▶ START HTTP SERVER ]"
            toggleServerBtn.setBackgroundColor(Color.parseColor("#00AA55"))
            
            wifiPill.setVisualEnabled(true)
            mobilePill.setVisualEnabled(true)
            allPill.setVisualEnabled(true)

            val ip = when (selectedBindingInterface) {
                "Wi-Fi" -> getInterfaceIp("Wi-Fi")
                "Mobile" -> getInterfaceIp("Mobile")
                else -> getLocalIpAddress()
            }
            serverAddressLabel.text = "🔴 SERVER OFFLINE\nWill bind to: http://$ip:8080"
            serverAddressLabel.setTextColor(Color.parseColor("#FF8800"))
            copyUrlBtn.setVisualEnabled(false)
        }

        toggleServerBtn.setVisualEnabled(modelLoaded && !modelLoading)
        if (!modelLoaded) {
            toggleServerBtn.text = "[ ▶ START SERVER (LOAD ENGINE FIRST) ]"
            toggleServerBtn.setBackgroundColor(Color.parseColor("#2A2A2A"))
        }
    }

    // --- TAB 3: API TEST SUITE & INTERACTIVE TERMINAL HARNESS ---

    private fun createTestTab() {
        val testContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        testScrollView = ScrollView(this).apply {
            visibility = View.GONE
            addView(testContainer)
        }

        val sectionTitle = TextView(this).apply {
            text = "API Test Harness & Model Shell"
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 14)
        }
        testContainer.addView(sectionTitle)

        // --- SECTION 1: QUICK PROMPT SHELL ---
        val shellCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#161616"))
                cornerRadius = 6f
                setStroke(1, Color.parseColor("#2A2A2A"))
            }
            background = bg
            setPadding(16, 16, 16, 16)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 14)
            }
            layoutParams = params
        }

        val shellHeader = TextView(this).apply {
            text = "> INTERACTIVE SHELL PROMPT"
            textSize = 12f
            setTextColor(Color.parseColor("#00FF66"))
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 8)
        }
        shellCard.addView(shellHeader)

        quickShellInput = EditText(this).apply {
            hint = "Type prompt here (auto-wrapped to /v1/chat)..."
            setHintTextColor(Color.parseColor("#555555"))
            textSize = 11f
            setTextColor(Color.parseColor("#00FF66"))
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            typeface = Typeface.MONOSPACE
            setPadding(14, 14, 14, 14)
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP
        }
        shellCard.addView(quickShellInput)

        val shellControlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 10, 0, 0)
        }

        quickShellStreamBtn = Button(this).apply {
            text = "[ STREAM: ON ]"
            setTextColor(Color.parseColor("#00FF66"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = params
            setOnClickListener {
                isQuickStreamEnabled = !isQuickStreamEnabled
                text = if (isQuickStreamEnabled) "[ STREAM: ON ]" else "[ STREAM: OFF ]"
                setTextColor(Color.parseColor(if (isQuickStreamEnabled) "#00FF66" else "#888888"))
            }
        }
        shellControlRow.addView(quickShellStreamBtn)

        quickShellSendBtn = Button(this).apply {
            text = "[ > SEND ]"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#00AA55"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
            setOnClickListener { executeQuickPrompt() }
        }
        shellControlRow.addView(quickShellSendBtn)
        shellCard.addView(shellControlRow)

        testContainer.addView(shellCard)

        // --- SECTION 2: ONE-CLICK TEST PRESETS ---
        val presetLabel = TextView(this).apply {
            text = "> TEST PRESETS"
            textSize = 11f
            setTextColor(Color.parseColor("#888888"))
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 6)
        }
        testContainer.addView(presetLabel)

        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 14)
            }
            layoutParams = params
        }

        val pillWeightParams = {
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 6, 0)
            }
        }

        presetS1Btn = Button(this).apply {
            text = "[ S1 RECALL ]"
            setTextColor(Color.parseColor("#CCCCCC"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 9f
            typeface = Typeface.MONOSPACE
            layoutParams = pillWeightParams()
            setOnClickListener { executePresetS1Recall() }
        }
        presetRow.addView(presetS1Btn)

        presetStreamBtn = Button(this).apply {
            text = "[ STREAM ]"
            setTextColor(Color.parseColor("#CCCCCC"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 9f
            typeface = Typeface.MONOSPACE
            layoutParams = pillWeightParams()
            setOnClickListener { executePresetStream() }
        }
        presetRow.addView(presetStreamBtn)

        presetS2QueueBtn = Button(this).apply {
            text = "[ S2 QUEUE ]"
            setTextColor(Color.parseColor("#00FF66"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 9f
            typeface = Typeface.MONOSPACE
            layoutParams = pillWeightParams()
            setOnClickListener { executePresetQueueStress() }
        }
        presetRow.addView(presetS2QueueBtn)

        presetHealthBtn = Button(this).apply {
            text = "[ HEALTH ]"
            setTextColor(Color.parseColor("#CCCCCC"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 9f
            typeface = Typeface.MONOSPACE
            val lastParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = lastParams
            setOnClickListener { executePresetHealth() }
        }
        presetRow.addView(presetHealthBtn)

        testContainer.addView(presetRow)

        // --- SECTION 3: COLLAPSIBLE RAW JSON / PROTOCOL SELECTOR ---
        rawJsonToggleBtn = Button(this).apply {
            text = "[ ▸ RAW JSON & ENDPOINT HARNESS ]"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#181818"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 10)
            }
            layoutParams = params
            setOnClickListener {
                isRawJsonVisible = !isRawJsonVisible
                rawJsonContainer.visibility = if (isRawJsonVisible) View.VISIBLE else View.GONE
                text = if (isRawJsonVisible) "[ ▾ RAW JSON & ENDPOINT HARNESS ]" else "[ ▸ RAW JSON & ENDPOINT HARNESS ]"
            }
        }
        testContainer.addView(rawJsonToggleBtn)

        rawJsonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#141414"))
                cornerRadius = 6f
                setStroke(1, Color.parseColor("#2A2A2A"))
            }
            background = bg
            setPadding(14, 14, 14, 14)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 14)
            }
            layoutParams = params
        }

        // Protocol Selection
        val protocolRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 10)
        }

        openAiPill = Button(this).apply {
            text = "[ OpenAI API ]"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 6, 0)
            }
            layoutParams = params
            setOnClickListener { setProtocolMode("OpenAI") }
        }
        protocolRow.addView(openAiPill)

        ollamaPill = Button(this).apply {
            text = "[ Ollama API ]"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#161616"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
            setOnClickListener { setProtocolMode("Ollama") }
        }
        protocolRow.addView(ollamaPill)
        rawJsonContainer.addView(protocolRow)

        // Route Selectors
        val epRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 10)
        }

        epPill1 = Button(this).apply {
            text = "GET /health"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 9f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 6, 0)
            }
            layoutParams = params
            setOnClickListener { setEndpointSelection(0) }
        }
        epRow.addView(epPill1)

        epPill2 = Button(this).apply {
            text = "GET /v1/models"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#161616"))
            textSize = 9f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 6, 0)
            }
            layoutParams = params
            setOnClickListener { setEndpointSelection(1) }
        }
        epRow.addView(epPill2)

        epPill3 = Button(this).apply {
            text = "POST /v1/chat"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#161616"))
            textSize = 9f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
            setOnClickListener { setEndpointSelection(2) }
        }
        epRow.addView(epPill3)
        rawJsonContainer.addView(epRow)

        // Template chips row
        chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 8)
        }
        val chipScroller = HorizontalScrollView(this).apply {
            addView(chipRow)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 8)
            }
            layoutParams = params
        }
        rawJsonContainer.addView(chipScroller)

        payloadEditor = EditText(this).apply {
            textSize = 10f
            setTextColor(Color.parseColor("#00FF66"))
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            setPadding(12, 12, 12, 12)
            typeface = Typeface.MONOSPACE
            minLines = 4
            maxLines = 6
            gravity = Gravity.TOP
            isEnabled = false
        }
        rawJsonContainer.addView(payloadEditor)

        runRawTestBtn = Button(this).apply {
            text = "[ ▶ EXECUTE RAW REQUEST ]"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#00AA55"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 10, 0, 0)
            }
            layoutParams = params
            setOnClickListener { executeRawApiTest() }
        }
        rawJsonContainer.addView(runRawTestBtn)

        testContainer.addView(rawJsonContainer)

        // --- SECTION 4: DIAGNOSTICS ROW ---
        val metricsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 12)
        }

        val boxParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
            setMargins(0, 0, 8, 0)
        }

        val createMetricBox = { title: String, metricRef: (TextView) -> Unit ->
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#161616"))
                    cornerRadius = 6f
                    setStroke(1, Color.parseColor("#2A2A2A"))
                }
                background = bg
                setPadding(10, 10, 10, 10)
            }
            val t = TextView(this).apply {
                text = title
                textSize = 9f
                setTextColor(Color.parseColor("#888888"))
                typeface = Typeface.MONOSPACE
            }
            val valView = TextView(this).apply {
                text = "---"
                textSize = 11f
                setTextColor(Color.WHITE)
                typeface = Typeface.MONOSPACE
            }
            metricRef(valView)
            box.addView(t)
            box.addView(valView)
            box
        }

        metricsRow.addView(createMetricBox("LATENCY") { latencyMetric = it }.apply { layoutParams = boxParams })
        metricsRow.addView(createMetricBox("HTTP STATUS") { statusMetric = it }.apply { layoutParams = boxParams })
        val lastBoxParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        metricsRow.addView(createMetricBox("PAYLOAD SIZE") { sizeMetric = it }.apply { layoutParams = lastBoxParams })
        testContainer.addView(metricsRow)

        // --- SECTION 5: CONSOLE OUTPUT ---
        testConsole = CollapsibleLogConsole(this, "API Response Console:")
        testContainer.addView(testConsole)

        refreshPayloadEditor()
    }

    private fun setProtocolMode(mode: String) {
        if (activeProtocol == mode) return
        activeProtocol = mode

        val openaiActive = mode == "OpenAI"
        openAiPill.setBackgroundColor(Color.parseColor(if (openaiActive) "#222222" else "#161616"))
        openAiPill.setTextColor(Color.parseColor(if (openaiActive) "#FFFFFF" else "#888888"))
        ollamaPill.setBackgroundColor(Color.parseColor(if (!openaiActive) "#222222" else "#161616"))
        ollamaPill.setTextColor(Color.parseColor(if (!openaiActive) "#FFFFFF" else "#888888"))

        if (openaiActive) {
            epPill1.text = "GET /health"
            epPill2.text = "GET /v1/models"
            epPill3.text = "POST /v1/chat"
            epPill3.visibility = View.VISIBLE
        } else {
            epPill1.text = "GET /api/tags"
            epPill2.text = "POST /api/chat"
            epPill3.visibility = View.GONE
        }
        setEndpointSelection(0)
    }

    private fun setEndpointSelection(index: Int) {
        selectedEndpointIndex = index

        epPill1.setBackgroundColor(Color.parseColor(if (index == 0) "#222222" else "#161616"))
        epPill1.setTextColor(Color.parseColor(if (index == 0) "#FFFFFF" else "#888888"))
        epPill2.setBackgroundColor(Color.parseColor(if (index == 1) "#222222" else "#161616"))
        epPill2.setTextColor(Color.parseColor(if (index == 1) "#FFFFFF" else "#888888"))
        epPill3.setBackgroundColor(Color.parseColor(if (index == 2) "#222222" else "#161616"))
        epPill3.setTextColor(Color.parseColor(if (index == 2) "#FFFFFF" else "#888888"))

        refreshPayloadEditor()
    }

    private fun refreshPayloadEditor() {
        val isGet = if (activeProtocol == "OpenAI") selectedEndpointIndex < 2 else selectedEndpointIndex == 0
        chipRow.removeAllViews()

        if (isGet) {
            payloadEditor.setText("[GET Request - No Payload Body]")
            payloadEditor.isEnabled = false
            payloadEditor.setBackgroundColor(Color.parseColor("#141414"))
            payloadEditor.setTextColor(Color.parseColor("#555555"))
            chipRow.addView(TextView(this).apply {
                text = "No parameters for GET routes"
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#555555"))
            })
        } else {
            payloadEditor.isEnabled = true
            payloadEditor.setBackgroundColor(Color.parseColor("#0A0A0A"))
            payloadEditor.setTextColor(Color.parseColor("#00FF66"))

            val chipParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 6, 0)
            }

            if (activeProtocol == "OpenAI") {
                val body1 = "{\n  \"model\": \"meta-llama-3-8b-instruct\",\n  \"messages\": [\n    {\"role\": \"user\", \"content\": \"Hello local LLM!\"}\n  ],\n  \"temperature\": 0.7\n}"
                val body2 = "{\n  \"model\": \"meta-llama-3-8b-instruct\",\n  \"messages\": [\n    {\"role\": \"user\", \"content\": \"Explain gravity in 1 line.\"}\n  ],\n  \"temperature\": 0.2,\n  \"max_tokens\": 50,\n  \"top_p\": 0.9\n}"
                val body3 = "{\n  \"model\": \"meta-llama-3-8b-instruct\",\n  \"messages\": [\n    {\"role\": \"user\", \"content\": \"Count from 1 to 5.\"}\n  ],\n  \"temperature\": 0.8,\n  \"stream\": true\n}"

                payloadEditor.setText(body1)

                val createChip = { label: String, text: String ->
                    Button(this).apply {
                        this.text = label
                        textSize = 9f
                        typeface = Typeface.MONOSPACE
                        setTextColor(Color.parseColor("#CCCCCC"))
                        setBackgroundColor(Color.parseColor("#222222"))
                        setPadding(10, 4, 10, 4)
                        setOnClickListener { payloadEditor.setText(text) }
                    }
                }
                chipRow.addView(createChip("[ Simple ]", body1), chipParams)
                chipRow.addView(createChip("[ Custom ]", body2), chipParams)
                chipRow.addView(createChip("[ Stream ]", body3), chipParams)
            } else {
                val body1 = "{\n  \"model\": \"llama3\",\n  \"messages\": [\n    {\"role\": \"user\", \"content\": \"hello!\"}\n  ],\n  \"stream\": false\n}"
                val body2 = "{\n  \"model\": \"llama3\",\n  \"messages\": [\n    {\"role\": \"user\", \"content\": \"Suggest 1 dog name.\"}\n  ],\n  \"options\": {\"temperature\": 0.3, \"num_predict\": 40},\n  \"stream\": false\n}"
                val body3 = "{\n  \"model\": \"llama3\",\n  \"messages\": [\n    {\"role\": \"user\", \"content\": \"Count to 3.\"}\n  ],\n  \"stream\": true\n}"

                payloadEditor.setText(body1)

                val createChip = { label: String, text: String ->
                    Button(this).apply {
                        this.text = label
                        textSize = 9f
                        typeface = Typeface.MONOSPACE
                        setTextColor(Color.parseColor("#CCCCCC"))
                        setBackgroundColor(Color.parseColor("#222222"))
                        setPadding(10, 4, 10, 4)
                        setOnClickListener { payloadEditor.setText(text) }
                    }
                }
                chipRow.addView(createChip("[ Basic ]", body1), chipParams)
                chipRow.addView(createChip("[ Custom ]", body2), chipParams)
                chipRow.addView(createChip("[ Stream ]", body3), chipParams)
            }
        }
    }

    private fun escapeJson(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun executeQuickPrompt() {
        val prompt = quickShellInput.text.toString().trim()
        if (prompt.isEmpty()) {
            Toast.makeText(this, "Please enter a prompt", Toast.LENGTH_SHORT).show()
            return
        }

        val jsonBody = """
            {
              "model": "edge-model",
              "messages": [
                {"role": "user", "content": "${escapeJson(prompt)}"}
              ],
              "temperature": 0.7,
              "stream": $isQuickStreamEnabled
            }
        """.trimIndent()

        executeHttpCall(
            method = "POST",
            path = "/v1/chat/completions",
            body = jsonBody,
            isStream = isQuickStreamEnabled,
            triggerButton = quickShellSendBtn,
            buttonDefaultText = "[ > SEND ]"
        )
    }

    private fun executePresetS1Recall() {
        val jsonBody = """
            {
              "model": "edge-model",
              "messages": [
                {"role": "user", "content": "My secret code is ALPHA-77. Remember it."},
                {"role": "assistant", "content": "Understood. Your secret code is ALPHA-77."},
                {"role": "user", "content": "What is my secret code?"}
              ],
              "temperature": 0.2,
              "stream": false
            }
        """.trimIndent()

        executeHttpCall(
            method = "POST",
            path = "/v1/chat/completions",
            body = jsonBody,
            isStream = false,
            triggerButton = presetS1Btn,
            buttonDefaultText = "[ S1 RECALL ]"
        )
    }

    private fun executePresetStream() {
        val jsonBody = """
            {
              "model": "edge-model",
              "messages": [
                {"role": "user", "content": "Count slowly from 1 to 5 with commas."}
              ],
              "temperature": 0.7,
              "stream": true
            }
        """.trimIndent()

        executeHttpCall(
            method = "POST",
            path = "/v1/chat/completions",
            body = jsonBody,
            isStream = true,
            triggerButton = presetStreamBtn,
            buttonDefaultText = "[ STREAM ]"
        )
    }

    private fun executePresetHealth() {
        executeHttpCall(
            method = "GET",
            path = "/health",
            body = null,
            isStream = false,
            triggerButton = presetHealthBtn,
            buttonDefaultText = "[ HEALTH ]"
        )
    }

    private fun executeRawApiTest() {
        var method = "GET"
        var path = "/health"
        var body: String? = null

        if (activeProtocol == "OpenAI") {
            when (selectedEndpointIndex) {
                0 -> { method = "GET"; path = "/health" }
                1 -> { method = "GET"; path = "/v1/models" }
                2 -> { method = "POST"; path = "/v1/chat/completions"; body = payloadEditor.text.toString() }
            }
        } else {
            when (selectedEndpointIndex) {
                0 -> { method = "GET"; path = "/api/tags" }
                1 -> { method = "POST"; path = "/api/chat"; body = payloadEditor.text.toString() }
            }
        }

        val isStream = body?.contains("\"stream\": true") == true || body?.contains("\"stream\":true") == true

        executeHttpCall(
            method = method,
            path = path,
            body = body,
            isStream = isStream,
            triggerButton = runRawTestBtn,
            buttonDefaultText = "[ ▶ EXECUTE RAW REQUEST ]"
        )
    }

    private fun executePresetQueueStress() {
        if (!LlmServerService.isServiceRunning) {
            ServerConsole.log(LogCategory.UI, "Queue Stress: Blocked. HTTP server is offline.")
            Toast.makeText(this, "HTTP Server is offline", Toast.LENGTH_SHORT).show()
            return
        }

        val activeHost = if (LlmServerService.activeBindHost == "0.0.0.0") "localhost" else LlmServerService.activeBindHost
        testConsole.setText(">>> S2 QUEUE STRESS TEST: Dispatching 5 concurrent HTTP requests to verify FIFO serialization and HTTP 429 capacity backpressure...\n(Max queue capacity: 4 slots)\n\n")

        latencyMetric.text = "---"
        statusMetric.text = "STRESS..."
        statusMetric.setTextColor(Color.parseColor("#FFBB33"))
        sizeMetric.text = "5 calls"

        presetS2QueueBtn.setVisualEnabled(false)
        presetS2QueueBtn.text = "Running 5x..."

        Thread {
            val results = java.util.Collections.synchronizedList(mutableListOf<String>())
            val countDownLatch = java.util.concurrent.CountDownLatch(5)
            val startTime = System.currentTimeMillis()

            for (i in 1..5) {
                Thread {
                    val reqStart = System.currentTimeMillis()
                    var code = -1
                    var respSnippet = ""
                    try {
                        val conn = URL("http://$activeHost:8080/v1/chat/completions").openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.connectTimeout = 5000
                        conn.readTimeout = 125000
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.doOutput = true

                        val body = """{"model":"edge-model","messages":[{"role":"user","content":"Stress task #$i"}],"stream":false}"""
                        val bytes = body.toByteArray(StandardCharsets.UTF_8)
                        conn.setFixedLengthStreamingMode(bytes.size)
                        conn.outputStream.use { it.write(bytes) }

                        code = conn.responseCode
                        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                        val raw = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
                        respSnippet = if (raw.length > 80) raw.take(80) + "..." else raw
                    } catch (e: Exception) {
                        code = -1
                        respSnippet = "Err: ${e.message}"
                    } finally {
                        val elapsed = System.currentTimeMillis() - reqStart
                        val tag = when (code) {
                            200 -> "[ 200 OK (FIFO Sequenced) ]"
                            429 -> "[ 429 RATE LIMIT EXCEEDED (Queue Guard Verified!) ]"
                            else -> "[ HTTP $code ]"
                        }
                        val line = "Req #$i: $tag in ${elapsed}ms -> $respSnippet"
                        results.add(line)
                        runOnUiThread {
                            testConsole.append(line + "\n")
                        }
                        countDownLatch.countDown()
                    }
                }.start()
            }

            countDownLatch.await()
            val totalTime = System.currentTimeMillis() - startTime

            runOnUiThread {
                presetS2QueueBtn.setVisualEnabled(true)
                presetS2QueueBtn.text = "[ S2 QUEUE ]"
                latencyMetric.text = "${totalTime}ms"
                statusMetric.text = "STRESS DONE"
                statusMetric.setTextColor(Color.parseColor("#00FF66"))
                testConsole.append("\n>>> STRESS TEST COMPLETE in ${totalTime}ms. RequestQueue S2 verification finished.\n")
            }
        }.start()
    }

    private fun executeHttpCall(
        method: String,
        path: String,
        body: String?,
        isStream: Boolean,
        triggerButton: Button? = null,
        buttonDefaultText: String = ""
    ) {
        if (!LlmServerService.isServiceRunning) {
            ServerConsole.log(LogCategory.UI, "Test Suite: Blocked. HTTP server is offline.")
            Toast.makeText(this, "HTTP Server is offline", Toast.LENGTH_SHORT).show()
            return
        }

        val activeHost = if (LlmServerService.activeBindHost == "0.0.0.0") "localhost" else LlmServerService.activeBindHost
        val requestString = "$method $path HTTP/1.1\nHost: $activeHost:8080\nContent-Type: application/json\nAccept: ${if (isStream) "text/event-stream" else "application/json"}\n\n${body ?: ""}"

        testConsole.setText(">>> HTTP REQUEST:\n$requestString\n\n----------------------------\n\n<<< RESPONSE:\n")
        latencyMetric.text = "---"
        statusMetric.text = "..."
        statusMetric.setTextColor(Color.WHITE)
        sizeMetric.text = "---"

        isTestingApi = true
        activeTestButton = triggerButton
        defaultTestButtonText = buttonDefaultText
        testStartTime = System.currentTimeMillis()
        triggerButton?.setVisualEnabled(false)
        testTimerHandler.post(testTimerRunnable)

        Thread {
            val start = System.currentTimeMillis()
            var code = -1
            val responseSb = StringBuilder()
            var success = false

            try {
                val conn = URL("http://$activeHost:8080$path").openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.connectTimeout = 5000
                conn.readTimeout = 125000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", if (isStream) "text/event-stream" else "application/json")

                if (method == "POST" && body != null) {
                    conn.doOutput = true
                    val b = body.toByteArray(StandardCharsets.UTF_8)
                    conn.setFixedLengthStreamingMode(b.size)
                    conn.outputStream.use { it.write(b) }
                }

                code = conn.responseCode
                success = code in 200..299
                val stream = if (success) conn.inputStream else conn.errorStream

                if (isStream && success && stream != null) {
                    val reader = stream.bufferedReader(StandardCharsets.UTF_8)
                    var line = reader.readLine()
                    while (line != null) {
                        responseSb.append(line).append("\n")
                        val currentLine = line
                        runOnUiThread {
                            testConsole.append(currentLine + "\n")
                            sizeMetric.text = "${responseSb.length} B"
                        }
                        line = reader.readLine()
                    }
                } else {
                    val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
                    responseSb.append(text)
                    runOnUiThread {
                        testConsole.append(text)
                    }
                }
            } catch (e: Exception) {
                val errText = "Connection Exception: ${e.message}\nEnsure background daemon is active and engine loaded."
                responseSb.append(errText)
                runOnUiThread {
                    testConsole.append(errText)
                }
            }

            val diff = System.currentTimeMillis() - start
            val finalResp = responseSb.toString()
            runOnUiThread {
                isTestingApi = false
                triggerButton?.setVisualEnabled(true)
                triggerButton?.text = buttonDefaultText

                latencyMetric.text = "${diff}ms"
                statusMetric.text = when (code) {
                    200 -> "200 OK"
                    429 -> "429 RATE LIMIT"
                    500 -> "500 ERR"
                    -1 -> "CONN ERR"
                    else -> "$code"
                }
                statusMetric.setTextColor(
                    when {
                        code in 200..299 -> Color.parseColor("#00FF66")
                        code == 429 -> Color.parseColor("#FF8800")
                        else -> Color.parseColor("#FF4444")
                    }
                )
                sizeMetric.text = "${finalResp.length} B"
            }
        }.start()
    }

    // --- TAB 4: CONSOLIDATED SYSTEM LOGS LAYOUT ---

    private fun createLogsTab() {
        val logsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        logsScrollView = ScrollView(this).apply {
            visibility = View.GONE
            addView(logsContainer)
        }

        val sectionTitle = TextView(this).apply {
            text = "Consolidated System Logs"
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 14)
        }
        logsContainer.addView(sectionTitle)

        // Action Toolbar: Copy & Clear buttons
        val toolbarRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 10)
            }
            layoutParams = params
        }

        copyLogsBtn = Button(this).apply {
            text = "[ 📋 COPY ALL ]"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = params
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Server Logs", fullSystemConsole.getText())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@MainActivity, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
        toolbarRow.addView(copyLogsBtn)

        clearLogsBtn = Button(this).apply {
            text = "[ 🗑️ CLEAR LOGS ]"
            setTextColor(Color.parseColor("#FF6666"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
            setOnClickListener {
                ServerConsole.clear()
                refreshSystemLogsView()
                Toast.makeText(this@MainActivity, "Logs buffer cleared", Toast.LENGTH_SHORT).show()
            }
        }
        toolbarRow.addView(clearLogsBtn)
        logsContainer.addView(toolbarRow)

        // Category filter pills inside horizontal scroll view
        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 12)
        }
        val horizontalScroll = HorizontalScrollView(this).apply {
            addView(filterRow)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 10)
            }
            layoutParams = params
        }
        logsContainer.addView(horizontalScroll)

        val pillParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 6, 0)
        }

        filterAllBtn = Button(this).apply {
            text = "All Logs"
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setOnClickListener { setLogsFilter("ALL") }
        }
        filterRow.addView(filterAllBtn, pillParams)

        filterServerBtn = Button(this).apply {
            text = "Server"
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setOnClickListener { setLogsFilter("SERVER") }
        }
        filterRow.addView(filterServerBtn, pillParams)

        filterEngineBtn = Button(this).apply {
            text = "Engine"
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setOnClickListener { setLogsFilter("ENGINE") }
        }
        filterRow.addView(filterEngineBtn, pillParams)

        filterUiBtn = Button(this).apply {
            text = "UI"
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setOnClickListener { setLogsFilter("UI") }
        }
        filterRow.addView(filterUiBtn, pillParams)

        fullSystemConsole = CollapsibleLogConsole(this, "Live System Console Output:")
        logsContainer.addView(fullSystemConsole)

        setLogsFilter("ALL")
    }

    private fun setLogsFilter(filter: String) {
        activeLogFilter = filter

        val isAll = filter == "ALL"
        val isServer = filter == "SERVER"
        val isEngine = filter == "ENGINE"
        val isUi = filter == "UI"

        filterAllBtn.setBackgroundColor(Color.parseColor(if (isAll) "#2A2A2A" else "#161616"))
        filterAllBtn.setTextColor(Color.parseColor(if (isAll) "#00FF66" else "#888888"))

        filterServerBtn.setBackgroundColor(Color.parseColor(if (isServer) "#2A2A2A" else "#161616"))
        filterServerBtn.setTextColor(Color.parseColor(if (isServer) "#00FF66" else "#888888"))

        filterEngineBtn.setBackgroundColor(Color.parseColor(if (isEngine) "#2A2A2A" else "#161616"))
        filterEngineBtn.setTextColor(Color.parseColor(if (isEngine) "#00FF66" else "#888888"))

        filterUiBtn.setBackgroundColor(Color.parseColor(if (isUi) "#2A2A2A" else "#161616"))
        filterUiBtn.setTextColor(Color.parseColor(if (isUi) "#00FF66" else "#888888"))

        refreshSystemLogsView()
    }

    private fun refreshSystemLogsView() {
        if (!::fullSystemConsole.isInitialized) return

        val category = when (activeLogFilter) {
            "SERVER" -> LogCategory.SERVER
            "ENGINE" -> LogCategory.ENGINE
            "UI" -> LogCategory.UI
            else -> null
        }

        val list = ServerConsole.getLogs(category)
        val sb = StringBuilder()
        for (entry in list) {
            sb.append(entry.toFormattedString()).append("\n")
        }
        fullSystemConsole.setText(sb.toString().trimEnd())

        // Also update filter labels with line counts
        filterAllBtn.text = "ALL (${ServerConsole.getLogs().size})"
        filterServerBtn.text = "SERVER (${ServerConsole.getLogs(LogCategory.SERVER).size})"
        filterEngineBtn.text = "ENGINE (${ServerConsole.getLogs(LogCategory.ENGINE).size})"
        filterUiBtn.text = "UI (${ServerConsole.getLogs(LogCategory.UI).size})"
    }

    private fun refreshConsoleLogs() {
        refreshSystemLogsView()
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = java.util.Collections.list(NetworkInterface.getNetworkInterfaces())
            // 1. Prioritize Wi-Fi interfaces first
            for (ni in interfaces) {
                val name = ni.name.lowercase()
                if (name.contains("wlan") || name.contains("ap") || name.contains("eth") || name.contains("tiwlan")) {
                    for (address in java.util.Collections.list(ni.inetAddresses)) {
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            return address.hostAddress ?: "127.0.0.1"
                        }
                    }
                }
            }
            // 2. Fallback: any active interface that is NOT mobile
            for (ni in interfaces) {
                val name = ni.name.lowercase()
                val isMobile = name.contains("rmnet") || name.contains("ccmni") || name.contains("ppp") || name.contains("wwan") || name.contains("rmnet_data")
                if (!isMobile && !ni.isLoopback) {
                    for (address in java.util.Collections.list(ni.inetAddresses)) {
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            return address.hostAddress ?: "127.0.0.1"
                        }
                    }
                }
            }
            // 3. Last resort: any non-loopback IPv4 address
            for (ni in interfaces) {
                for (address in java.util.Collections.list(ni.inetAddresses)) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return "127.0.0.1"
    }

    private fun getInterfaceIp(type: String): String {
        try {
            val interfaces = java.util.Collections.list(NetworkInterface.getNetworkInterfaces())
            // First pass: try matching name directly
            for (ni in interfaces) {
                val name = ni.name.lowercase()
                val isWifiName = name.contains("wlan") || name.contains("ap") || name.contains("eth") || name.contains("tiwlan")
                val isMobileName = name.contains("rmnet") || name.contains("ccmni") || name.contains("ppp") || name.contains("wwan") || name.contains("rmnet_data")
                
                if (type == "Wi-Fi" && !isWifiName) continue
                if (type == "Mobile" && !isMobileName) continue
                
                for (addr in java.util.Collections.list(ni.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }

            // Second pass fallback: if Wi-Fi requested but not found, try to find any interface that is NOT mobile and has IPv4
            if (type == "Wi-Fi") {
                for (ni in interfaces) {
                    val name = ni.name.lowercase()
                    val isMobileName = name.contains("rmnet") || name.contains("ccmni") || name.contains("ppp") || name.contains("wwan") || name.contains("rmnet_data")
                    if (!isMobileName && !ni.isLoopback) {
                        for (addr in java.util.Collections.list(ni.inetAddresses)) {
                            if (!addr.isLoopbackAddress && addr is Inet4Address) {
                                return addr.hostAddress ?: "127.0.0.1"
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return "127.0.0.1"
    }

    private fun logAllInterfaces() {
        try {
            val interfaces = java.util.Collections.list(NetworkInterface.getNetworkInterfaces())
            val sb = StringBuilder("Discovered Interfaces:\n")
            for (ni in interfaces) {
                val addresses = java.util.Collections.list(ni.inetAddresses)
                val ipv4List = addresses.filterIsInstance<Inet4Address>().map { it.hostAddress }
                if (ipv4List.isNotEmpty()) {
                    sb.append("  • ${ni.name} (Up: ${ni.isUp}): ${ipv4List.joinToString(", ")}\n")
                }
            }
            ServerConsole.log(LogCategory.UI, sb.toString().trimEnd())
        } catch (e: Exception) {
            ServerConsole.log(LogCategory.UI, "Error listing interfaces: ${e.message}")
        }
    }

    private fun launchBatterySettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            ServerConsole.log(LogCategory.UI, "Failed to open battery optimization options: ${e.message}")
        }
    }

    private fun openModelFilePicker() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            filePickerLauncher.launch(intent)
        } catch (e: Exception) {
            ServerConsole.log(LogCategory.UI, "Failed to open model file picker: ${e.message}")
        }
    }

    private fun updateModelDisplay() {
        val name = selectedModelName
        if (selectedModelPath != null) {
            modelFileLabel.text = "Selected: $name"
            modelFileLabel.setTextColor(Color.parseColor("#00FF66"))
        } else {
            modelFileLabel.text = "Selected: No file selected"
            modelFileLabel.setTextColor(Color.parseColor("#888888"))
        }
    }

    private fun setInterfaceBinding(mode: String) {
        if (selectedBindingInterface == mode) return
        selectedBindingInterface = mode

        getSharedPreferences("llm_server_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("selected_interface", mode)
            .apply()

        val isWifi = mode == "Wi-Fi"
        val isMobile = mode == "Mobile"
        val isAll = mode == "All"

        if (isMobile) {
            ServerConsole.log(LogCategory.UI, "⚠️ MOBILE BIND NOTICE: Binding to Mobile Cellular Interface is subject to Carrier-Grade NAT (CGNAT) and carrier firewall blocks. External devices on other networks will not be able to query the server on this IP.")
        }

        wifiPill.setBackgroundColor(Color.parseColor(if (isWifi) "#3366BB" else "#222222"))
        wifiPill.setTextColor(Color.parseColor(if (isWifi) "#FFFFFF" else "#888888"))

        mobilePill.setBackgroundColor(Color.parseColor(if (isMobile) "#3366BB" else "#222222"))
        mobilePill.setTextColor(Color.parseColor(if (isMobile) "#FFFFFF" else "#888888"))

        allPill.setBackgroundColor(Color.parseColor(if (isAll) "#3366BB" else "#222222"))
        allPill.setTextColor(Color.parseColor(if (isAll) "#FFFFFF" else "#888888"))

        refreshIpAddress()
        logAllInterfaces()
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        ServerConsole.log(LogCategory.UI, "Requesting storage manage access...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = android.net.Uri.parse("package:${packageName}")
                }
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 102)
        }
    }
}
