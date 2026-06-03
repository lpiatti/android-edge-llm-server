package com.edge.llm.server.ui

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
    private lateinit var quickPromptEdit: EditText
    private lateinit var quickRunBtn: Button
    private lateinit var quickInferenceConsole: CollapsibleLogConsole
    
    private var selectedModelPath: String? = null
    private var selectedModelName: String = "No file selected"
    private var isMockEngineSelected = true
    private var isGpuSelected = false

    // Tab 2 (Server Daemon) Views
    private lateinit var toggleServerBtn: Button
    private lateinit var batteryBtn: Button
    private lateinit var wifiPill: Button
    private lateinit var mobilePill: Button
    private lateinit var allPill: Button
    private lateinit var serverConsole: CollapsibleLogConsole
    private var selectedBindingInterface = "All"

    // Tab 3 (API Test Suite) Views
    private lateinit var openAiPill: Button
    private lateinit var ollamaPill: Button
    private lateinit var epPill1: Button
    private lateinit var epPill2: Button
    private lateinit var epPill3: Button
    private lateinit var chipRow: LinearLayout
    private lateinit var payloadEditor: EditText
    private lateinit var runTestBtn: Button
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
                
                val path = UriHelper.resolveUriToPath(this, uri)
                if (path != null) {
                    selectedModelPath = path
                    selectedModelName = File(path).name
                    ServerConsole.log(LogCategory.UI, "Selected model: $selectedModelName (path: $path)")
                    updateModelDisplay()
                } else {
                    ServerConsole.log(LogCategory.UI, "Failed to resolve local path from picker.")
                }
            }
        }
    }

    // Timer logic during test suite
    private val testTimerHandler = Handler(Looper.getMainLooper())
    private var testStartTime: Long = 0
    private var isTestingApi = false
    private val testTimerRunnable = object : Runnable {
        override fun run() {
            if (isTestingApi) {
                val elapsedSec = (System.currentTimeMillis() - testStartTime) / 1000.0
                runTestBtn.text = "Testing... %.1fs".format(elapsedSec)
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
            }
            statusUpdateHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        checkPermissionsAndProceed()
    }

    override fun onResume() {
        super.onResume()
        statusUpdateHandler.post(statusUpdateRunnable)
        if (currentViewState == "PERMISSION" && hasNotificationPermission() && hasStoragePermission()) {
            checkPermissionsAndProceed()
        }
        if (currentViewState == "MAIN") {
            updateStickyStatus()
            refreshIpAddress()
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
            text = "Engine"
            textSize = 10f
            setOnClickListener { switchTab(0) }
        }
        tabBar.addView(modelTabBtn, btnParams)

        serverTabBtn = Button(this).apply {
            text = "Daemon"
            textSize = 10f
            setOnClickListener { switchTab(1) }
        }
        tabBar.addView(serverTabBtn, btnParams)

        testTabBtn = Button(this).apply {
            text = "Tester"
            textSize = 10f
            setOnClickListener { switchTab(2) }
        }
        tabBar.addView(testTabBtn, btnParams)

        logsTabBtn = Button(this).apply {
            text = "Logs"
            textSize = 10f
            setOnClickListener { switchTab(3) }
        }
        val logsBtnParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        tabBar.addView(logsTabBtn, logsBtnParams)

        rootLayout.addView(tabBar)

        // Hook console log updates
        ServerConsole.logListener = { entry ->
            runOnUiThread {
                if (entry.message == "CLEAR_LOGS") {
                    serverConsole.clear()
                    fullSystemConsole.clear()
                } else {
                    // Tab 2 console displays only the last 3 logs
                    if (entry.category == LogCategory.SERVER) {
                        serverConsole.setText(ServerConsole.getLogs(LogCategory.SERVER).takeLast(3).joinToString("\n") { it.toFormattedString() })
                    }
                    refreshSystemLogsView()
                }
            }
        }

        switchTab(0)
        refreshConsoleLogs()
        refreshPayloadEditor()
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
        statusIsland.update(
            serverRunning = LlmServerService.isServiceRunning,
            bindHost = LlmServerService.activeBindHost,
            modelLoaded = ModelManager.isModelLoaded,
            isMock = ModelManager.isMockMode,
            modelName = ModelManager.activeModelName,
            modelLoading = ModelManager.isLoading,
            isGpu = ModelManager.isGpuActive
        )
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
            text = "Model Engine Setup"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }
        modelContainer.addView(sectionTitle)

        // Engine Selectors
        val engineModeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 12)
        }

        mockPillModel = Button(this).apply {
            text = "Mock Engine"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3366BB"))
            textSize = 11f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = params
            setOnClickListener { setModelMockMode(true) }
        }
        engineModeRow.addView(mockPillModel)

        realPillModel = Button(this).apply {
            text = "Real .litertlm"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 11f
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
            visibility = View.GONE // Show only if real model selected
        }

        cpuModelPill = Button(this).apply {
            text = "Backend: CPU"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3366BB"))
            textSize = 10f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = params
            setOnClickListener { setHardwareBackend(false) }
        }
        backendSelectorRow.addView(cpuModelPill)

        gpuModelPill = Button(this).apply {
            text = "Backend: GPU"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 10f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
            setOnClickListener { setHardwareBackend(true) }
        }
        backendSelectorRow.addView(gpuModelPill)
        modelContainer.addView(backendSelectorRow)

        // Native SAF file picker card
        pickerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.parseColor("#1C1C1C"))
            visibility = View.GONE
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 16)
            }
            layoutParams = params
        }

        modelFileLabel = TextView(this).apply {
            text = "Selected: No file selected"
            textSize = 11f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 0, 0, 12)
        }
        pickerCard.addView(modelFileLabel)

        pickerBtn = Button(this).apply {
            text = "📂 Select Model File (.litertlm)"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#444444"))
            textSize = 11f
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
            text = "Initialize Engine"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#22AA55"))
            textSize = 12f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = params
            setOnClickListener { initializeEngine() }
        }
        actionsRow.addView(initModelBtn)

        stopModelBtn = Button(this).apply {
            text = "Unload Engine"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC0000"))
            textSize = 12f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
            setOnClickListener { unloadEngine() }
        }
        actionsRow.addView(stopModelBtn)
        modelContainer.addView(actionsRow)

        // Quick Direct Prompt Test Row
        val testLabel = TextView(this).apply {
            text = "Quick Inference Test:"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 8, 0, 8)
        }
        modelContainer.addView(testLabel)

        val quickInputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 12)
            }
            layoutParams = params
        }

        quickPromptEdit = EditText(this).apply {
            hint = "Ask the model something..."
            textSize = 11f
            setTextColor(Color.parseColor("#555555"))
            setBackgroundColor(Color.parseColor("#1C1C1C"))
            setPadding(16, 12, 16, 12)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = params
            isEnabled = false
        }
        quickInputRow.addView(quickPromptEdit)

        quickRunBtn = Button(this).apply {
            text = "Send"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#333333"))
            textSize = 11f
            isEnabled = false
            setOnClickListener { runQuickInferenceTest() }
        }
        quickInputRow.addView(quickRunBtn)
        modelContainer.addView(quickInputRow)

        quickInferenceConsole = CollapsibleLogConsole(this, "Inference Output Logs:")
        modelContainer.addView(quickInferenceConsole)
    }

    private fun setModelMockMode(isMock: Boolean) {
        if (isMockEngineSelected == isMock) return
        isMockEngineSelected = isMock

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
        
        initModelBtn.setVisualEnabled(!loaded && !loading)
        stopModelBtn.setVisualEnabled(loaded && !loading)
        
        mockPillModel.setVisualEnabled(!loaded && !loading)
        realPillModel.setVisualEnabled(!loaded && !loading)
        cpuModelPill.setVisualEnabled(!loaded && !loading)
        gpuModelPill.setVisualEnabled(!loaded && !loading)
        pickerBtn.setVisualEnabled(!loaded && !loading)

        modelTabBtn.setVisualEnabled(!loading)
        serverTabBtn.setVisualEnabled(!loading)
        testTabBtn.setVisualEnabled(!loading)
        logsTabBtn.setVisualEnabled(!loading)

        if (loaded) {
            quickPromptEdit.isEnabled = true
            quickPromptEdit.setBackgroundColor(Color.parseColor("#0A0A0A"))
            quickPromptEdit.setTextColor(Color.parseColor("#00FF66"))
            quickRunBtn.setVisualEnabled(true)
            quickRunBtn.setBackgroundColor(Color.parseColor("#22AA55"))
        } else {
            quickPromptEdit.isEnabled = false
            quickPromptEdit.setBackgroundColor(Color.parseColor("#1C1C1C"))
            quickPromptEdit.setTextColor(Color.parseColor("#555555"))
            quickRunBtn.setVisualEnabled(false)
            quickRunBtn.setBackgroundColor(Color.parseColor("#333333"))
        }
    }

    private fun runQuickInferenceTest() {
        val prompt = quickPromptEdit.text.toString()
        if (prompt.isEmpty()) return

        quickInferenceConsole.setText(">>> Prompt: \"$prompt\"\n<<< Awaiting generation stream...\n")
        quickRunBtn.setVisualEnabled(false)

        val provider = ModelManager.activeProvider
        Thread {
            try {
                kotlinx.coroutines.runBlocking {
                    provider.generateStream(prompt).collect { token ->
                        runOnUiThread {
                            quickInferenceConsole.append(token)
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    quickInferenceConsole.append("\n\nINFERENCE ERROR: ${e.message}")
                }
            } finally {
                runOnUiThread {
                    quickRunBtn.setVisualEnabled(true)
                }
            }
        }.start()
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
            text = "Ktor Server Daemon Control"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }
        serverContainer.addView(sectionTitle)

        // Start / Stop Button
        toggleServerBtn = Button(this).apply {
            text = "Start HTTP Daemon"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3366BB"))
            setPadding(24, 20, 24, 20)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 16)
            }
            layoutParams = params
            setOnClickListener { toggleServerDaemon() }
        }
        serverContainer.addView(toggleServerBtn)

        // Network Interface Selector
        val bindLabel = TextView(this).apply {
            text = "Select Network Interface Bind:"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 0, 0, 8)
        }
        serverContainer.addView(bindLabel)

        val bindRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        wifiPill = Button(this).apply {
            text = "Wi-Fi Only"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 10f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = params
            setOnClickListener { setInterfaceBinding("Wi-Fi") }
        }
        bindRow.addView(wifiPill)

        mobilePill = Button(this).apply {
            text = "Mobile Only"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 10f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = params
            setOnClickListener { setInterfaceBinding("Mobile") }
        }
        bindRow.addView(mobilePill)

        allPill = Button(this).apply {
            text = "All Interfaces"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3366BB"))
            textSize = 10f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
            setOnClickListener { setInterfaceBinding("All") }
        }
        bindRow.addView(allPill)
        serverContainer.addView(bindRow)

        batteryBtn = Button(this).apply {
            text = "Bypass CPU Standby Throttling"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#444444"))
            textSize = 11f
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 16)
            }
            layoutParams = params
            setOnClickListener { launchBatterySettings() }
        }
        serverContainer.addView(batteryBtn)

        serverConsole = CollapsibleLogConsole(this, "Daemon Console Logs (Last 3 entries):")
        serverContainer.addView(serverConsole)
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

        if (active) {
            toggleServerBtn.text = "Stop HTTP Server"
            toggleServerBtn.setBackgroundColor(Color.parseColor("#CC0000"))
            
            wifiPill.setVisualEnabled(false)
            mobilePill.setVisualEnabled(false)
            allPill.setVisualEnabled(false)

            initModelBtn.setVisualEnabled(false)
            stopModelBtn.setVisualEnabled(false)
        } else {
            toggleServerBtn.text = "Start HTTP Server"
            toggleServerBtn.setBackgroundColor(Color.parseColor("#3366BB"))
            
            wifiPill.setVisualEnabled(true)
            mobilePill.setVisualEnabled(true)
            allPill.setVisualEnabled(true)

            initModelBtn.setVisualEnabled(!modelLoaded)
            stopModelBtn.setVisualEnabled(modelLoaded)
        }

        toggleServerBtn.setVisualEnabled(modelLoaded)
        if (!modelLoaded) {
            toggleServerBtn.text = "Start Server (Load Engine First)"
        }
    }

    // --- TAB 3: API TEST SUITE LAYOUT (Wrapped inside ScrollView) ---

    private fun createTestTab() {
        val testContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        testScrollView = ScrollView(this).apply {
            visibility = View.GONE
            addView(testContainer)
        }

        val sectionTitle = TextView(this).apply {
            text = "API Test Harness"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }
        testContainer.addView(sectionTitle)

        // Protocol Selection
        val protocolRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        openAiPill = Button(this).apply {
            text = "OpenAI API"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3366BB"))
            textSize = 11f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = params
            setOnClickListener { setProtocolMode("OpenAI") }
        }
        protocolRow.addView(openAiPill)

        ollamaPill = Button(this).apply {
            text = "Ollama API"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 11f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
            setOnClickListener { setProtocolMode("Ollama") }
        }
        protocolRow.addView(ollamaPill)
        testContainer.addView(protocolRow)

        // Route Selectors
        val epLabel = TextView(this).apply {
            text = "Select Route to Test:"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 0, 0, 8)
        }
        testContainer.addView(epLabel)

        val epRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        epPill1 = Button(this).apply {
            text = "GET /health"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3366BB"))
            textSize = 9f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = params
            setOnClickListener { setEndpointSelection(0) }
        }
        epRow.addView(epPill1)

        epPill2 = Button(this).apply {
            text = "GET /v1/models"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 9f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = params
            setOnClickListener { setEndpointSelection(1) }
        }
        epRow.addView(epPill2)

        epPill3 = Button(this).apply {
            text = "POST /v1/chat"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 9f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
            setOnClickListener { setEndpointSelection(2) }
        }
        epRow.addView(epPill3)
        testContainer.addView(epRow)

        // JSON Chips for optional inference parameters
        val chipLabel = TextView(this).apply {
            text = "Payload Templates (with optional parameters):"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 0, 0, 6)
        }
        testContainer.addView(chipLabel)

        chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 12)
        }
        
        val hScroller = HorizontalScrollView(this).apply {
            addView(chipRow)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 16)
            }
            layoutParams = params
        }
        testContainer.addView(hScroller)

        // Editor
        val editLabel = TextView(this).apply {
            text = "JSON Request Body:"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 0, 0, 8)
        }
        testContainer.addView(editLabel)

        payloadEditor = EditText(this).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#555555"))
            setBackgroundColor(Color.parseColor("#1C1C1C"))
            setPadding(16, 16, 16, 16)
            typeface = Typeface.MONOSPACE
            minLines = 4
            maxLines = 6
            gravity = Gravity.TOP
            isEnabled = false
        }
        testContainer.addView(payloadEditor)

        runTestBtn = Button(this).apply {
            text = "Run API Suite Test"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#22AA55"))
            setPadding(20, 16, 20, 16)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 16, 0, 16)
            }
            layoutParams = params
            setOnClickListener { executeClientApiTest() }
        }
        testContainer.addView(runTestBtn)

        // Diagnostics row
        val metricsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        val boxParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
            setMargins(0, 0, 8, 0)
        }

        val createMetricBox = { title: String, metricRef: (TextView) -> Unit ->
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#1C1C1C"))
                setPadding(12, 12, 12, 12)
            }
            val t = TextView(this).apply {
                text = title
                textSize = 8f
                setTextColor(Color.parseColor("#888888"))
            }
            val valView = TextView(this).apply {
                text = "---"
                textSize = 12f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
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

        testConsole = CollapsibleLogConsole(this, "API Client Output:")
        testContainer.addView(testConsole)
    }

    private fun setProtocolMode(mode: String) {
        if (activeProtocol == mode) return
        activeProtocol = mode

        val openaiActive = mode == "OpenAI"
        openAiPill.setBackgroundColor(Color.parseColor(if (openaiActive) "#3366BB" else "#222222"))
        openAiPill.setTextColor(Color.parseColor(if (openaiActive) "#FFFFFF" else "#888888"))
        ollamaPill.setBackgroundColor(Color.parseColor(if (!openaiActive) "#3366BB" else "#222222"))
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

        epPill1.setBackgroundColor(Color.parseColor(if (index == 0) "#3366BB" else "#222222"))
        epPill1.setTextColor(Color.parseColor(if (index == 0) "#FFFFFF" else "#888888"))
        epPill2.setBackgroundColor(Color.parseColor(if (index == 1) "#3366BB" else "#222222"))
        epPill2.setTextColor(Color.parseColor(if (index == 1) "#FFFFFF" else "#888888"))
        epPill3.setBackgroundColor(Color.parseColor(if (index == 2) "#3366BB" else "#222222"))
        epPill3.setTextColor(Color.parseColor(if (index == 2) "#FFFFFF" else "#888888"))

        refreshPayloadEditor()
    }

    private fun refreshPayloadEditor() {
        val isGet = if (activeProtocol == "OpenAI") selectedEndpointIndex < 2 else selectedEndpointIndex == 0
        
        // Remove old chips
        chipRow.removeAllViews()

        if (isGet) {
            payloadEditor.setText("[GET Request - No Payload Body]")
            payloadEditor.isEnabled = false
            payloadEditor.setBackgroundColor(Color.parseColor("#1C1C1C"))
            payloadEditor.setTextColor(Color.parseColor("#555555"))
            chipRow.addView(TextView(this).apply {
                text = "No parameters for GET routes"
                textSize = 10f
                setTextColor(Color.parseColor("#555555"))
            })
        } else {
            payloadEditor.isEnabled = true
            payloadEditor.setBackgroundColor(Color.parseColor("#0A0A0A"))
            payloadEditor.setTextColor(Color.parseColor("#00FF66"))

            val chipParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 8, 0)
            }

            if (activeProtocol == "OpenAI") {
                // OpenAI payload options
                val body1 = "{\n  \"model\": \"meta-llama-3-8b-instruct\",\n  \"messages\": [\n    {\n      \"role\": \"user\",\n      \"content\": \"Hello local LLM!\"\n    }\n  ],\n  \"temperature\": 0.7\n}"
                val body2 = "{\n  \"model\": \"meta-llama-3-8b-instruct\",\n  \"messages\": [\n    {\n      \"role\": \"user\",\n      \"content\": \"Explain gravity in 1 line.\"\n    }\n  ],\n  \"temperature\": 0.2,\n  \"max_tokens\": 50,\n  \"top_p\": 0.9\n}"
                val body3 = "{\n  \"model\": \"meta-llama-3-8b-instruct\",\n  \"messages\": [\n    {\n      \"role\": \"user\",\n      \"content\": \"Count to 5.\"\n    }\n  ],\n  \"temperature\": 0.8,\n  \"stream\": true\n}"

                payloadEditor.setText(body1)

                chipRow.addView(Button(this).apply {
                    text = "📄 Simple"
                    textSize = 9f
                    setPadding(12, 4, 12, 4)
                    setOnClickListener { payloadEditor.setText(body1) }
                }, chipParams)

                chipRow.addView(Button(this).apply {
                    text = "⚙️ Custom (Temp/Tokens)"
                    textSize = 9f
                    setPadding(12, 4, 12, 4)
                    setOnClickListener { payloadEditor.setText(body2) }
                }, chipParams)

                chipRow.addView(Button(this).apply {
                    text = "🌊 Stream"
                    textSize = 9f
                    setPadding(12, 4, 12, 4)
                    setOnClickListener { payloadEditor.setText(body3) }
                }, chipParams)
            } else {
                // Ollama payload options
                val body1 = "{\n  \"model\": \"llama3\",\n  \"messages\": [\n    {\n      \"role\": \"user\",\n      \"content\": \"hello!\"\n    }\n  ],\n  \"stream\": false\n}"
                val body2 = "{\n  \"model\": \"llama3\",\n  \"messages\": [\n    {\n      \"role\": \"user\",\n      \"content\": \"Suggest 1 dog name.\"\n    }\n  ],\n  \"options\": {\n    \"temperature\": 0.3,\n    \"num_predict\": 40\n  },\n  \"stream\": false\n}"
                val body3 = "{\n  \"model\": \"llama3\",\n  \"messages\": [\n    {\n      \"role\": \"user\",\n      \"content\": \"Count to 3.\"\n    }\n  ],\n  \"stream\": true\n}"

                payloadEditor.setText(body1)

                chipRow.addView(Button(this).apply {
                    text = "📄 Basic"
                    textSize = 9f
                    setPadding(12, 4, 12, 4)
                    setOnClickListener { payloadEditor.setText(body1) }
                }, chipParams)

                chipRow.addView(Button(this).apply {
                    text = "⚙️ Custom (Temp/Predict)"
                    textSize = 9f
                    setPadding(12, 4, 12, 4)
                    setOnClickListener { payloadEditor.setText(body2) }
                }, chipParams)

                chipRow.addView(Button(this).apply {
                    text = "🌊 Stream"
                    textSize = 9f
                    setPadding(12, 4, 12, 4)
                    setOnClickListener { payloadEditor.setText(body3) }
                }, chipParams)
            }
        }
    }

    private fun executeClientApiTest() {
        if (!LlmServerService.isServiceRunning) {
            ServerConsole.log(LogCategory.UI, "Test Suite: Blocked. Ktor server is offline.")
            return
        }

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

        val activeHost = if (LlmServerService.activeBindHost == "0.0.0.0") "localhost" else LlmServerService.activeBindHost
        val requestString = "$method $path HTTP/1.1\nHost: $activeHost:8080\nContent-Type: application/json\n\n${body ?: ""}"
        
        testConsole.setText(">>> HTTP REQUEST:\n$requestString\n\n----------------------------\n\n<<< RESPONSE:\n")
        latencyMetric.text = "---"
        statusMetric.text = "..."
        statusMetric.setTextColor(Color.WHITE)
        sizeMetric.text = "---"

        // Live timer setup
        isTestingApi = true
        testStartTime = System.currentTimeMillis()
        runTestBtn.setVisualEnabled(false)
        testTimerHandler.post(testTimerRunnable)

        Thread {
            val start = System.currentTimeMillis()
            var code = -1
            var response = ""
            var success = false

            try {
                val conn = URL("http://$activeHost:8080$path").openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.connectTimeout = 5000
                conn.readTimeout = 60000 // 60 Seconds Read Timeout to prevent LLM timeouts
                conn.setRequestProperty("Content-Type", "application/json")

                if (method == "POST" && body != null) {
                    conn.doOutput = true
                    val b = body.encodeToByteArray()
                    conn.setFixedLengthStreamingMode(b.size)
                    conn.outputStream.use { it.write(b) }
                }

                code = conn.responseCode
                success = code in 200..299
                val stream = if (success) conn.inputStream else conn.errorStream
                response = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
            } catch (e: java.io.IOException) {
                response = "Connection exception: ${e.message}\n\nIs the background daemon running and the model fully initialized?"
            }

            val diff = System.currentTimeMillis() - start
            runOnUiThread {
                isTestingApi = false
                runTestBtn.setVisualEnabled(true)
                runTestBtn.text = "Run API Suite Test"
                
                latencyMetric.text = "${diff}ms"
                statusMetric.text = if (code != -1) "$code" else "ERR"
                statusMetric.setTextColor(if (success) Color.parseColor("#00FF66") else Color.parseColor("#FF4444"))
                sizeMetric.text = "${response.length} chars"
                testConsole.append(response)
            }
        }.start()
    }

    // --- TAB 4: SYSTEM LOGS LAYOUT (Wrapped inside ScrollView) ---

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
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }
        logsContainer.addView(sectionTitle)

        // Filter pills inside horizontal scroll view
        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }
        val horizontalScroll = HorizontalScrollView(this).apply {
            addView(filterRow)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 12)
            }
            layoutParams = params
        }
        logsContainer.addView(horizontalScroll)

        val pillParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 8, 0)
        }

        filterAllBtn = Button(this).apply {
            text = "All Logs"
            textSize = 10f
            setOnClickListener { setLogsFilter("ALL") }
        }
        filterRow.addView(filterAllBtn, pillParams)

        filterServerBtn = Button(this).apply {
            text = "Server"
            textSize = 10f
            setOnClickListener { setLogsFilter("SERVER") }
        }
        filterRow.addView(filterServerBtn, pillParams)

        filterEngineBtn = Button(this).apply {
            text = "Engine"
            textSize = 10f
            setOnClickListener { setLogsFilter("ENGINE") }
        }
        filterRow.addView(filterEngineBtn, pillParams)

        filterUiBtn = Button(this).apply {
            text = "UI"
            textSize = 10f
            setOnClickListener { setLogsFilter("UI") }
        }
        filterRow.addView(filterUiBtn, pillParams)

        fullSystemConsole = CollapsibleLogConsole(this, "Selectable Logs Output:")
        logsContainer.addView(fullSystemConsole)

        setLogsFilter("ALL")
    }

    private fun setLogsFilter(filter: String) {
        activeLogFilter = filter

        val isAll = filter == "ALL"
        val isServer = filter == "SERVER"
        val isEngine = filter == "ENGINE"
        val isUi = filter == "UI"

        filterAllBtn.setBackgroundColor(Color.parseColor(if (isAll) "#3366BB" else "#222222"))
        filterAllBtn.setTextColor(Color.parseColor(if (isAll) "#FFFFFF" else "#888888"))

        filterServerBtn.setBackgroundColor(Color.parseColor(if (isServer) "#3366BB" else "#222222"))
        filterServerBtn.setTextColor(Color.parseColor(if (isServer) "#FFFFFF" else "#888888"))

        filterEngineBtn.setBackgroundColor(Color.parseColor(if (isEngine) "#3366BB" else "#222222"))
        filterEngineBtn.setTextColor(Color.parseColor(if (isEngine) "#FFFFFF" else "#888888"))

        filterUiBtn.setBackgroundColor(Color.parseColor(if (isUi) "#3366BB" else "#222222"))
        filterUiBtn.setTextColor(Color.parseColor(if (isUi) "#FFFFFF" else "#888888"))

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
        filterAllBtn.text = "All Logs (${ServerConsole.getLogs().size})"
        filterServerBtn.text = "Server (${ServerConsole.getLogs(LogCategory.SERVER).size})"
        filterEngineBtn.text = "Engine (${ServerConsole.getLogs(LogCategory.ENGINE).size})"
        filterUiBtn.text = "UI (${ServerConsole.getLogs(LogCategory.UI).size})"
    }

    // --- OTHER HELPERS ---

    private fun refreshIpAddress() {
        val ip = when (selectedBindingInterface) {
            "Wi-Fi" -> getInterfaceIp("Wi-Fi")
            "Mobile" -> getInterfaceIp("Mobile")
            else -> getLocalIpAddress()
        }
        ServerConsole.log(LogCategory.UI, "Resolved active socket bind address: $ip")
    }

    private fun refreshConsoleLogs() {
        val cached = ServerConsole.getLogs()
        val sb = StringBuilder()
        for (entry in cached) {
            sb.append(entry.toFormattedString()).append("\n")
        }
        serverConsole.setText(ServerConsole.getLogs(LogCategory.SERVER).takeLast(3).joinToString("\n") { it.toFormattedString() })
        refreshSystemLogsView()
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                val addresses = ni.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
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
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                val name = ni.name.lowercase()
                if (type == "Wi-Fi" && !name.contains("wlan")) continue
                if (type == "Mobile" && !(name.contains("rmnet") || name.contains("ccmni") || name.contains("ppp") || name.contains("wwan"))) continue
                
                val addresses = ni.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return "127.0.0.1"
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

        val isWifi = mode == "Wi-Fi"
        val isMobile = mode == "Mobile"
        val isAll = mode == "All"

        wifiPill.setBackgroundColor(Color.parseColor(if (isWifi) "#3366BB" else "#222222"))
        wifiPill.setTextColor(Color.parseColor(if (isWifi) "#FFFFFF" else "#888888"))

        mobilePill.setBackgroundColor(Color.parseColor(if (isMobile) "#3366BB" else "#222222"))
        mobilePill.setTextColor(Color.parseColor(if (isMobile) "#FFFFFF" else "#888888"))

        allPill.setBackgroundColor(Color.parseColor(if (isAll) "#3366BB" else "#222222"))
        allPill.setTextColor(Color.parseColor(if (isAll) "#FFFFFF" else "#888888"))

        refreshIpAddress()
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
