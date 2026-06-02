package com.edge.llm.server.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.edge.llm.server.model.ModelManager
import com.edge.llm.server.service.LlmServerService
import com.edge.llm.server.util.ServerConsole
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * MainActivity: The primary control panel of our edge LLM server node.
 * Implements a robust state machine:
 * 1. CRASH DIAGNOSTICS: Safe display of startup exception dumps.
 * 2. PERMISSION ONBOARDING: Interactive setup of background execution requirements.
 * 3. MAIN DASHBOARD: Re-ordered simple and guided 3-tab layout.
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
    private lateinit var modelLayout: LinearLayout
    private lateinit var serverLayout: LinearLayout
    private lateinit var testLayout: LinearLayout
    private lateinit var modelTabBtn: Button
    private lateinit var serverTabBtn: Button
    private lateinit var testTabBtn: Button

    // Tab 1 (Model Engine) Views
    private lateinit var mockPillModel: Button
    private lateinit var realPillModel: Button
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
    private var isMockEngineSelected = true // Default mock

    // Tab 2 (Server Daemon) Views
    private lateinit var toggleServerBtn: Button
    private lateinit var batteryBtn: Button
    private lateinit var wifiPill: Button
    private lateinit var mobilePill: Button
    private lateinit var allPill: Button
    private lateinit var serverConsole: CollapsibleLogConsole
    private var selectedBindingInterface = "All" // "All", "Wi-Fi", "Mobile"

    // Tab 3 (API Test Suite) Views
    private lateinit var openAiPill: Button
    private lateinit var ollamaPill: Button
    private lateinit var epPill1: Button
    private lateinit var epPill2: Button
    private lateinit var epPill3: Button
    private lateinit var payloadEditor: EditText
    private lateinit var runTestBtn: Button
    private lateinit var latencyMetric: TextView
    private lateinit var statusMetric: TextView
    private lateinit var sizeMetric: TextView
    private lateinit var testConsole: CollapsibleLogConsole
    private var activeProtocol = "OpenAI"
    private var selectedEndpointIndex = 0

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
                    // Ignore on non-persistable providers
                }
                
                val path = UriHelper.resolveUriToPath(this, uri)
                if (path != null) {
                    selectedModelPath = path
                    selectedModelName = File(path).name
                    ServerConsole.log("SAF File Selected: $selectedModelName (Resolved path: $path)")
                    updateModelDisplay()
                } else {
                    ServerConsole.log("SAF Resolution Error: Failed to resolve local path.")
                }
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

        // Capture crash logs directly in a file on the device
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
                // Ignore writing errors
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Global base container
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(32, 32, 32, 32)
        }
        setContentView(rootLayout)

        // Evaluate crash state
        val crashFile = File(filesDir, "crash_log.txt")
        if (crashFile.exists()) {
            val elapsed = System.currentTimeMillis() - crashFile.lastModified()
            val twoHours = 2 * 60 * 60 * 1000 // 2 Hours in ms
            if (elapsed < twoHours) {
                val crashText = crashFile.readText()
                setupCrashDiagnosticsView(crashText)
                return
            } else {
                crashFile.delete() // Cleanup stale logs
            }
        }

        // Check permission state and proceed
        checkPermissionsAndProceed()
    }

    override fun onResume() {
        super.onResume()
        statusUpdateHandler.post(statusUpdateRunnable)
        if (currentViewState == "PERMISSION" && hasNotificationPermission() && hasStoragePermission()) {
            // Permissions granted, transition to Main Dashboard
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

    override fun onDestroy() {
        super.onDestroy()
        ServerConsole.logListener = null
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
            text = "⚠️ System Diagnostic Log"
            textSize = 20f
            setTextColor(Color.parseColor("#FF4444"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }
        rootLayout.addView(titleView)

        val descView = TextView(this).apply {
            text = "The application crashed during the previous execution. We bypassed auto-start procedures and active loads to prevent boot loops."
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
            }
            addView(tv)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
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
            text = "Server Permissions Setup"
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                setBackgroundColor(Color.parseColor("#1C1C1C"))
                val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, 16)
                }
                layoutParams = params
            }
            val cardTitle = TextView(this).apply {
                text = "🔔 Notification Alerts"
                textSize = 14f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }
            card.addView(cardTitle)
            val cardDesc = TextView(this).apply {
                text = "Required on Android 13+ to post persistent service status cards in the system drawer."
                textSize = 12f
                setTextColor(Color.parseColor("#888888"))
                setPadding(0, 4, 0, 12)
            }
            card.addView(cardDesc)
            val btn = Button(this).apply {
                text = "Enable Notifications"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#3366BB"))
                setOnClickListener { requestNotificationPermission() }
            }
            card.addView(btn)
            rootLayout.addView(card)
        }

        // Card 2: Storage Permission
        if (!hasStoragePermission()) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                setBackgroundColor(Color.parseColor("#1C1C1C"))
                val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, 24)
                }
                layoutParams = params
            }
            val cardTitle = TextView(this).apply {
                text = "📂 Storage & Model Access"
                textSize = 14f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }
            card.addView(cardTitle)
            val cardDesc = TextView(this).apply {
                text = "Required to allow loading of large .litertlm models directly from external files without duplicate copies."
                textSize = 12f
                setTextColor(Color.parseColor("#888888"))
                setPadding(0, 4, 0, 12)
            }
            card.addView(cardDesc)
            val btn = Button(this).apply {
                text = "Grant Storage Access"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#FF8800"))
                setOnClickListener { requestStoragePermission() }
            }
            card.addView(btn)
            rootLayout.addView(card)
        }

        val checkStatusBtn = Button(this).apply {
            text = "Refresh Setup Status"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#22AA55"))
            setOnClickListener { checkPermissionsAndProceed() }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 16, 0, 0)
            }
            layoutParams = params
        }
        rootLayout.addView(checkStatusBtn)
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

        // Initialize Tab layouts
        createModelTab()
        createServerTab()
        createTestTab()

        // Add all tabs to content container initially, but set GONE
        contentContainer.addView(modelLayout)
        contentContainer.addView(serverLayout)
        contentContainer.addView(testLayout)

        // 3. Bottom Tab Bar
        tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            setPadding(12, 12, 12, 12)
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutParams = params
        }

        modelTabBtn = Button(this).apply {
            text = "Model Engine"
            textSize = 10f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 4, 0)
            }
            layoutParams = params
            setOnClickListener { switchTab(0) }
        }
        tabBar.addView(modelTabBtn)

        serverTabBtn = Button(this).apply {
            text = "Server Daemon"
            textSize = 10f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 4, 0)
            }
            layoutParams = params
            setOnClickListener { switchTab(1) }
        }
        tabBar.addView(serverTabBtn)

        testTabBtn = Button(this).apply {
            text = "Test Suite"
            textSize = 10f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
            setOnClickListener { switchTab(2) }
        }
        tabBar.addView(testTabBtn)

        rootLayout.addView(tabBar)

        // Hook console log updates to the active log screen
        ServerConsole.logListener = { logLine ->
            runOnUiThread {
                if (logLine == "CLEAR_LOGS") {
                    serverConsole.clear()
                } else {
                    serverConsole.append(logLine + "\n")
                }
            }
        }

        // Default: Open Tab 1
        switchTab(0)
        refreshConsoleLogs()
        refreshPayloadEditor()
    }

    private fun switchTab(index: Int) {
        modelLayout.visibility = View.GONE
        serverLayout.visibility = View.GONE
        testLayout.visibility = View.GONE

        modelTabBtn.setBackgroundColor(Color.parseColor("#121212"))
        modelTabBtn.setTextColor(Color.parseColor("#888888"))
        serverTabBtn.setBackgroundColor(Color.parseColor("#121212"))
        serverTabBtn.setTextColor(Color.parseColor("#888888"))
        testTabBtn.setBackgroundColor(Color.parseColor("#121212"))
        testTabBtn.setTextColor(Color.parseColor("#888888"))

        when (index) {
            0 -> {
                modelLayout.visibility = View.VISIBLE
                modelTabBtn.setBackgroundColor(Color.parseColor("#3366BB"))
                modelTabBtn.setTextColor(Color.WHITE)
            }
            1 -> {
                serverLayout.visibility = View.VISIBLE
                serverTabBtn.setBackgroundColor(Color.parseColor("#3366BB"))
                serverTabBtn.setTextColor(Color.WHITE)
                refreshIpAddress()
            }
            2 -> {
                testLayout.visibility = View.VISIBLE
                testTabBtn.setBackgroundColor(Color.parseColor("#3366BB"))
                testTabBtn.setTextColor(Color.WHITE)
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
            modelLoading = ModelManager.isLoading
        )
    }

    // --- TAB 1: MODEL ENGINE LAYOUT ---

    private fun createModelTab() {
        modelLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        // Header Label
        val sectionTitle = TextView(this).apply {
            text = "LLM Engine Setup"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }
        modelLayout.addView(sectionTitle)

        // Engine Selectors
        val engineModeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
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
        modelLayout.addView(engineModeRow)

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
        modelLayout.addView(pickerCard)

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
        modelLayout.addView(actionsRow)

        // Quick Direct Prompt Test Row
        val testLabel = TextView(this).apply {
            text = "Quick Inference Test:"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 8, 0, 8)
        }
        modelLayout.addView(testLabel)

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
        modelLayout.addView(quickInputRow)

        quickInferenceConsole = CollapsibleLogConsole(this, "Inference Output Logs:")
        modelLayout.addView(quickInferenceConsole)
    }

    private fun setModelMockMode(isMock: Boolean) {
        if (isMockEngineSelected == isMock) return
        isMockEngineSelected = isMock

        mockPillModel.setBackgroundColor(Color.parseColor(if (isMock) "#3366BB" else "#222222"))
        mockPillModel.setTextColor(Color.parseColor(if (isMock) "#FFFFFF" else "#888888"))
        realPillModel.setBackgroundColor(Color.parseColor(if (!isMock) "#3366BB" else "#222222"))
        realPillModel.setTextColor(Color.parseColor(if (!isMock) "#FFFFFF" else "#888888"))

        pickerCard.visibility = if (isMock) View.GONE else View.VISIBLE
    }

    private fun openModelFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        filePickerLauncher.launch(intent)
    }

    private fun updateModelDisplay() {
        modelFileLabel.text = "Selected: $selectedModelName"
        modelFileLabel.setTextColor(Color.parseColor("#00FF66"))
    }

    private fun initializeEngine() {
        val isMock = isMockEngineSelected
        var path: String? = null
        if (!isMock) {
            path = selectedModelPath
            if (path == null) {
                ServerConsole.log("Engine Setup: Failed to initialize. No model selected.")
                return
            }
        }

        ServerConsole.log("Engine Setup: Commencing load sequence (isMock=$isMock, path=${path ?: "N/A"})...")
        lockUiForModelLoading(true)

        Thread {
            try {
                kotlinx.coroutines.runBlocking {
                    ModelManager.loadModel(path, isMock)
                }
                runOnUiThread {
                    ServerConsole.log("Engine Setup: Load sequence completed successfully.")
                    lockUiForModelLoading(false)
                }
            } catch (e: Exception) {
                val error = e.message ?: e.toString()
                runOnUiThread {
                    ServerConsole.log("Engine Setup CRITICAL ERROR: $error")
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
                ServerConsole.log("Engine Setup: Model unloaded successfully.")
                lockUiForModelLoading(false)
            }
        }.start()
    }

    private fun lockUiForModelLoading(loading: Boolean) {
        if (loading) {
            initModelBtn.isEnabled = false
            stopModelBtn.isEnabled = false
            mockPillModel.isEnabled = false
            realPillModel.isEnabled = false
            pickerBtn.isEnabled = false
            modelTabBtn.isEnabled = false
            serverTabBtn.isEnabled = false
            testTabBtn.isEnabled = false
        } else {
            updateModelUiState()
        }
    }

    private fun updateModelUiState() {
        val loaded = ModelManager.isModelLoaded
        val loading = ModelManager.isLoading
        
        initModelBtn.isEnabled = !loaded && !loading
        stopModelBtn.isEnabled = loaded && !loading
        
        mockPillModel.isEnabled = !loaded && !loading
        realPillModel.isEnabled = !loaded && !loading
        pickerBtn.isEnabled = !loaded && !loading

        modelTabBtn.isEnabled = !loading
        serverTabBtn.isEnabled = !loading
        testTabBtn.isEnabled = !loading

        if (loaded) {
            quickPromptEdit.isEnabled = true
            quickPromptEdit.setBackgroundColor(Color.parseColor("#0A0A0A"))
            quickPromptEdit.setTextColor(Color.parseColor("#00FF66"))
            quickRunBtn.isEnabled = true
            quickRunBtn.setBackgroundColor(Color.parseColor("#22AA55"))
        } else {
            quickPromptEdit.isEnabled = false
            quickPromptEdit.setBackgroundColor(Color.parseColor("#1C1C1C"))
            quickPromptEdit.setTextColor(Color.parseColor("#555555"))
            quickRunBtn.isEnabled = false
            quickRunBtn.setBackgroundColor(Color.parseColor("#333333"))
        }
    }

    private fun runQuickInferenceTest() {
        val prompt = quickPromptEdit.text.toString()
        if (prompt.isEmpty()) return

        quickInferenceConsole.setText(">>> Prompt: \"$prompt\"\n<<< Awaiting generation stream...\n")
        quickRunBtn.isEnabled = false

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
                    quickRunBtn.isEnabled = true
                }
            }
        }.start()
    }

    // --- TAB 2: SERVER DAEMON LAYOUT ---

    private fun createServerTab() {
        serverLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        val sectionTitle = TextView(this).apply {
            text = "Ktor Server Control"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }
        serverLayout.addView(sectionTitle)

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
        serverLayout.addView(toggleServerBtn)

        // Binding Interface labels
        val bindLabel = TextView(this).apply {
            text = "Select Network Interface Bind:"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 0, 0, 8)
        }
        serverLayout.addView(bindLabel)

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
        serverLayout.addView(bindRow)

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
        serverLayout.addView(batteryBtn)

        serverConsole = CollapsibleLogConsole(this, "Daemon Console Logs:")
        serverLayout.addView(serverConsole)
    }

    private fun toggleServerDaemon() {
        if (!ModelManager.isModelLoaded) {
            ServerConsole.log("Server Switch Blocked: No active model engine loaded. Switch to 'Model Engine' tab.")
            return
        }

        val intent = Intent(this, LlmServerService::class.java)
        if (LlmServerService.isServiceRunning) {
            ServerConsole.log("Stopping HTTP Daemon...")
            stopService(intent)
        } else {
            val bindHost = when (selectedBindingInterface) {
                "Wi-Fi" -> getInterfaceIp("Wi-Fi")
                "Mobile" -> getInterfaceIp("Mobile")
                else -> "0.0.0.0"
            }
            intent.putExtra("EXTRA_BIND_HOST", bindHost)
            ServerConsole.log("Requesting background start sequence on $bindHost...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    private fun updateServerUiState() {
        val active = LlmServerService.isServiceRunning
        
        // Enforce Interlocks on Model configurations when server runs
        val modelLoaded = ModelManager.isModelLoaded
        if (active) {
            toggleServerBtn.text = "Stop HTTP Server"
            toggleServerBtn.setBackgroundColor(Color.parseColor("#CC0000"))
            
            // Lock network binding
            wifiPill.isEnabled = false
            mobilePill.isEnabled = false
            allPill.isEnabled = false
            wifiPill.alpha = 0.5f
            mobilePill.alpha = 0.5f
            allPill.alpha = 0.5f

            // Block model changes while serving clients
            initModelBtn.isEnabled = false
            stopModelBtn.isEnabled = false
        } else {
            toggleServerBtn.text = "Start HTTP Server"
            toggleServerBtn.setBackgroundColor(Color.parseColor("#3366BB"))
            
            wifiPill.isEnabled = true
            mobilePill.isEnabled = true
            allPill.isEnabled = true
            wifiPill.alpha = 1.0f
            mobilePill.alpha = 1.0f
            allPill.alpha = 1.0f

            // Manage button permissions based on engine states
            initModelBtn.isEnabled = !modelLoaded
            stopModelBtn.isEnabled = modelLoaded
        }

        // Enable start daemon button only if an engine is loaded
        toggleServerBtn.isEnabled = modelLoaded
        if (!modelLoaded) {
            toggleServerBtn.alpha = 0.5f
            toggleServerBtn.text = "Start Server (Load Engine First)"
        } else {
            toggleServerBtn.alpha = 1.0f
        }
    }

    private fun setInterfaceBinding(mode: String) {
        if (selectedBindingInterface == mode) return
        selectedBindingInterface = mode

        wifiPill.setBackgroundColor(Color.parseColor(if (mode == "Wi-Fi") "#3366BB" else "#222222"))
        wifiPill.setTextColor(Color.parseColor(if (mode == "Wi-Fi") "#FFFFFF" else "#888888"))
        mobilePill.setBackgroundColor(Color.parseColor(if (mode == "Mobile") "#3366BB" else "#222222"))
        mobilePill.setTextColor(Color.parseColor(if (mode == "Mobile") "#FFFFFF" else "#888888"))
        allPill.setBackgroundColor(Color.parseColor(if (mode == "All") "#3366BB" else "#222222"))
        allPill.setTextColor(Color.parseColor(if (mode == "All") "#FFFFFF" else "#888888"))

        refreshIpAddress()
    }

    private fun refreshIpAddress() {
        val ip = when (selectedBindingInterface) {
            "Wi-Fi" -> getInterfaceIp("Wi-Fi")
            "Mobile" -> getInterfaceIp("Mobile")
            else -> getLocalIpAddress()
        }
        ServerConsole.log("Network bindings updated: resolved IP endpoint -> $ip")
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

    private fun refreshConsoleLogs() {
        val cached = ServerConsole.getLogs()
        val sb = StringBuilder()
        for (line in cached) {
            sb.append(line).append("\n")
        }
        serverConsole.setText(sb.toString().trimEnd())
    }

    private fun launchBatterySettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            ServerConsole.log("OS settings access error: ${e.message}")
        }
    }

    // --- TAB 3: API TEST SUITE LAYOUT ---

    private fun createTestTab() {
        testLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        val sectionTitle = TextView(this).apply {
            text = "API Test Harness"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }
        testLayout.addView(sectionTitle)

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
        testLayout.addView(protocolRow)

        // Endpoint Selectors
        val epLabel = TextView(this).apply {
            text = "Select Route to Test:"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 0, 0, 8)
        }
        testLayout.addView(epLabel)

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
        testLayout.addView(epRow)

        // Editor
        val editLabel = TextView(this).apply {
            text = "JSON Payload Body:"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 0, 0, 8)
        }
        testLayout.addView(editLabel)

        payloadEditor = EditText(this).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#555555"))
            setBackgroundColor(Color.parseColor("#1C1C1C"))
            setPadding(16, 16, 16, 16)
            typeface = Typeface.MONOSPACE
            minLines = 3
            maxLines = 5
            gravity = Gravity.TOP
            isEnabled = false
        }
        testLayout.addView(payloadEditor)

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
        testLayout.addView(runTestBtn)

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
        
        testLayout.addView(metricsRow)

        testConsole = CollapsibleLogConsole(this, "API Response Stream Output:")
        testLayout.addView(testConsole)
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
        if (isGet) {
            payloadEditor.setText("[GET Request - No Payload Body]")
            payloadEditor.isEnabled = false
            payloadEditor.setBackgroundColor(Color.parseColor("#1C1C1C"))
            payloadEditor.setTextColor(Color.parseColor("#555555"))
        } else {
            if (activeProtocol == "OpenAI") {
                payloadEditor.setText("{\n  \"model\": \"meta-llama-3-8b-instruct\",\n  \"messages\": [\n    {\n      \"role\": \"user\",\n      \"content\": \"Hello local LLM!\"\n    }\n  ],\n  \"temperature\": 0.7\n}")
            } else {
                payloadEditor.setText("{\n  \"model\": \"llama3\",\n  \"messages\": [\n    {\n      \"role\": \"user\",\n      \"content\": \"why is the sky blue?\"\n    }\n  ],\n  \"stream\": false\n}")
            }
            payloadEditor.isEnabled = true
            payloadEditor.setBackgroundColor(Color.parseColor("#0A0A0A"))
            payloadEditor.setTextColor(Color.parseColor("#00FF66"))
        }
    }

    private fun executeClientApiTest() {
        if (!LlmServerService.isServiceRunning) {
            ServerConsole.log("[Test Suite] Blocked: Ktor HTTP server is offline. Load model and start daemon first.")
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

        Thread {
            val start = System.currentTimeMillis()
            var code = -1
            var response = ""
            var success = false

            try {
                val conn = URL("http://$activeHost:8080$path").openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.connectTimeout = 3000
                conn.readTimeout = 4000
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
                response = "Connection exception: ${e.message}"
            }

            val diff = System.currentTimeMillis() - start
            runOnUiThread {
                latencyMetric.text = "${diff}ms"
                statusMetric.text = if (code != -1) "$code" else "ERR"
                statusMetric.setTextColor(if (success) Color.parseColor("#00FF66") else Color.parseColor("#FF4444"))
                sizeMetric.text = "${response.length} chars"
                testConsole.append(response)
            }
        }.start()
    }

    // --- SYSTEM PERMISSION CHECK UTILS ---

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
        ServerConsole.log("Requesting storage credentials...")
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
