package com.edge.llm.server

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
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * MainActivity: The primary dashboard panel of our edge LLM server node.
 *
 * Implements a premium Bottom Tab Navigation:
 * - Tab 1 ("Server"): Daemon starting, OS battery settings whitelisting, LAN network address,
 *   and real-time monospaced server console logging.
 * - Tab 2 ("Test"): Dynamic local API test harness, allowing direct client execution of OpenAI and Ollama
 *   endpoints, calculating round-trip latency, status codes, payload metrics, and rendering responses.
 */
class MainActivity : AppCompatActivity() {

    // Tab Layout Framework
    private lateinit var serverLayout: LinearLayout
    private lateinit var modelLayout: LinearLayout
    private lateinit var testLayout: LinearLayout
    private lateinit var serverTabBtn: Button
    private lateinit var modelTabBtn: Button
    private lateinit var testTabBtn: Button

    // Tab 1 (Server Console) Views
    private lateinit var statusCard: TextView
    private lateinit var toggleButton: Button
    private lateinit var ipTextView: TextView
    private lateinit var logTextViewServer: TextView
    private lateinit var logScrollViewServer: ScrollView

    // Tab 2 (API Client Test Suite) Views
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
    private lateinit var logTextViewTest: TextView
    private lateinit var logScrollViewTest: ScrollView

    // Tab 3 (Model Manager Panel) Views
    private lateinit var modelSpinner: android.widget.Spinner
    private lateinit var grantPermissionBtn: Button
    private lateinit var initModelBtn: Button
    private lateinit var stopModelBtn: Button
    private lateinit var modelStatusCard: TextView
    private lateinit var permissionStatusCard: TextView
    private lateinit var mockPillModel: Button
    private lateinit var realPillModel: Button
    private var isModelMockModeSelected = true // Default is Mock Mode
    private var discoveredModelFiles = emptyList<java.io.File>()

    // Quick Inference Test Views
    private lateinit var quickPromptEdit: EditText
    private lateinit var quickRunBtn: Button
    private lateinit var quickLogTextView: TextView
    private lateinit var quickLogScrollView: ScrollView

    // Interface selector pills
    private lateinit var wifiPill: Button
    private lateinit var mobilePill: Button
    private lateinit var allPill: Button
    private var selectedBindingInterface = "All" // "All", "Wi-Fi", "Mobile"

    // Execution States
    private var activeProtocol = "OpenAI" // "OpenAI" or "Ollama"
    private var selectedEndpointIndex = 0 // 0, 1, 2

    private val statusUpdateHandler = Handler(Looper.getMainLooper())
    private val statusUpdateRunnable = object : Runnable {
        override fun run() {
            updateUiState()
            updateModelUiState()
            statusUpdateHandler.postDelayed(this, 1000) // Poll every 1 second
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Global Uncaught Exception Handler to capture crash logs directly in a file on the device
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val file = java.io.File(filesDir, "crash_log.txt")
                java.io.FileOutputStream(file).use { fos ->
                    java.io.PrintStream(fos).use { ps ->
                        ps.println("CRASH REPORT (MAIN) - ${java.util.Date()}")
                        ps.println("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})")
                        throwable.printStackTrace(ps)
                    }
                }
            } catch (e: Exception) {
                // Ignore writing error
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Check for previous session crash logs and cache them in the ServerConsole immediately
        try {
            val crashFile = java.io.File(filesDir, "crash_log.txt")
            if (crashFile.exists()) {
                val crashText = crashFile.readText()
                ServerConsole.log("⚠️ PREVIOUS SESSION CRASH DETECTED:\n$crashText")
                crashFile.delete()
            }
        } catch (e: Exception) {
            // Ignore
        }

        // Request runtime Notification permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // 1. Root Container (Vertical linear layout housing Content Area + Tab Bar)
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212")) // Slate premium dark background
        }

        // 2. Content Area (Weights 1.0 to absorb remaining vertical space)
        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
            layoutParams = params
        }

        // --- SECTION A: Tab 1 (Server Daemon Panel) ---
        serverLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 32, 40, 32)
            visibility = View.VISIBLE
        }

        // App Title Block
        val serverTitle = TextView(this).apply {
            text = "Edge LLM Server Console"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        serverLayout.addView(serverTitle)

        val serverSubtitle = TextView(this).apply {
            text = "Android Edge AI local inference daemon"
            textSize = 13f
            setTextColor(Color.parseColor("#777777"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 24)
        }
        serverLayout.addView(serverSubtitle)

        // IP visual Card
        ipTextView = TextView(this).apply {
            text = "LAN Access Endpoint: RESOLVING..."
            textSize = 14f
            setTextColor(Color.parseColor("#33B5E5"))
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1C1C1C"))
            setPadding(24, 24, 24, 24)
            typeface = Typeface.MONOSPACE
        }
        serverLayout.addView(ipTextView)

        // Status Card
        statusCard = TextView(this).apply {
            text = "SERVER STATE: OFFLINE"
            textSize = 14f
            setTextColor(Color.parseColor("#FF4444"))
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(24, 24, 24, 24)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 0)
            }
            layoutParams = params
        }
        serverLayout.addView(statusCard)

        // Target Network Bind Interface Selector
        val interfaceLabel = TextView(this).apply {
            text = "Target Network Bind Interface:"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 16, 0, 8)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        serverLayout.addView(interfaceLabel)

        val interfacePillRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            layoutParams = params
        }

        wifiPill = Button(this).apply {
            text = "Wi-Fi Only"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 10f
            setPadding(12, 8, 12, 8)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = params
            setOnClickListener { setInterfaceBinding("Wi-Fi") }
        }
        interfacePillRow.addView(wifiPill)

        mobilePill = Button(this).apply {
            text = "Mobile Only"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 10f
            setPadding(12, 8, 12, 8)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = params
            setOnClickListener { setInterfaceBinding("Mobile") }
        }
        interfacePillRow.addView(mobilePill)

        allPill = Button(this).apply {
            text = "All Interfaces"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3366BB"))
            textSize = 10f
            setPadding(12, 8, 12, 8)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
            setOnClickListener { setInterfaceBinding("All") }
        }
        interfacePillRow.addView(allPill)
        serverLayout.addView(interfacePillRow)

        // Button Control Row
        val controlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 20, 0, 20)
            }
            layoutParams = params
        }

        toggleButton = Button(this).apply {
            text = "Start Daemon"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0099CC"))
            setPadding(20, 16, 20, 16)
            textSize = 13f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f).apply {
                setMargins(0, 0, 16, 0)
            }
            layoutParams = params
            setOnClickListener { toggleServerDaemon() }
        }
        controlRow.addView(toggleButton)

        val batteryBtn = Button(this).apply {
            text = "Ignore Throttling"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#444444"))
            setPadding(16, 16, 16, 16)
            textSize = 12f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
            setOnClickListener { launchBatterySettings() }
        }
        controlRow.addView(batteryBtn)
        serverLayout.addView(controlRow)

        // Monospaced Server Logger Header
        val serverConsoleHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val serverConsoleLabel = TextView(this).apply {
            text = "Server System logs:"
            textSize = 13f
            setTextColor(Color.parseColor("#888888"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        serverConsoleHeader.addView(serverConsoleLabel)
        val clearServerLogsBtn = Button(this).apply {
            text = "Clear"
            setTextColor(Color.parseColor("#FF4444"))
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 11f
            setOnClickListener { ServerConsole.clear() }
        }
        serverConsoleHeader.addView(clearServerLogsBtn)
        serverLayout.addView(serverConsoleHeader)

        // Monospaced Server Console Scroll View
        logTextViewServer = TextView(this).apply {
            textSize = 10f
            setTextColor(Color.parseColor("#00FF66"))
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            setPadding(16, 16, 16, 16)
            typeface = Typeface.MONOSPACE
            text = "System active.\nListening for daemon server logs..."
        }
        logScrollViewServer = ScrollView(this).apply {
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f).apply {
                setMargins(0, 8, 0, 0)
            }
            layoutParams = params
            addView(logTextViewServer)
        }
        serverLayout.addView(logScrollViewServer)

        contentLayout.addView(serverLayout)

        // --- SECTION D: Tab 3 (Model Manager Panel) ---
        modelLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 32, 40, 32)
            visibility = View.GONE // Hidden initially
        }

        val modelTitle = TextView(this).apply {
            text = "Model Lifecycle Manager"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        modelLayout.addView(modelTitle)

        val modelSubtitle = TextView(this).apply {
            text = "Load local .litertlm models or use mock"
            textSize = 13f
            setTextColor(Color.parseColor("#777777"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 16)
        }
        modelLayout.addView(modelSubtitle)

        // Storage Permission Alert Card
        permissionStatusCard = TextView(this).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
            typeface = Typeface.MONOSPACE
        }
        modelLayout.addView(permissionStatusCard)

        grantPermissionBtn = Button(this).apply {
            text = "Grant Storage Access"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF8800"))
            textSize = 11f
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }
            layoutParams = params
            setOnClickListener { requestStoragePermission() }
        }
        modelLayout.addView(grantPermissionBtn)

        // Switch pills: Mock Mode vs Real Model
        val modeSelectionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 16)
        }

        mockPillModel = Button(this).apply {
            text = "Mock Engine"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3366BB")) // Active initially
            textSize = 11f
            setPadding(16, 8, 16, 8)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = params
            setOnClickListener { setModelMode(true) }
        }
        modeSelectionRow.addView(mockPillModel)

        realPillModel = Button(this).apply {
            text = "Real .litertlm File"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 11f
            setPadding(16, 8, 16, 8)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = params
            setOnClickListener { setModelMode(false) }
        }
        modeSelectionRow.addView(realPillModel)
        modelLayout.addView(modeSelectionRow)

        // Dropdown visual area
        val dropdownLabel = TextView(this).apply {
            text = "Select Local Quantized Model (.litertlm):"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 0, 0, 8)
        }
        modelLayout.addView(dropdownLabel)

        modelSpinner = android.widget.Spinner(this).apply {
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            layoutParams = params
            isEnabled = false // Disabled initially since Mock is selected
        }
        modelLayout.addView(modelSpinner)

        // Control Buttons (Initialize & Unload)
        val modelControlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            layoutParams = params
        }

        initModelBtn = Button(this).apply {
            text = "Initialize Model"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0099CC"))
            textSize = 12f
            setPadding(16, 12, 16, 12)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = params
            setOnClickListener { triggerInitializeModel() }
        }
        modelControlRow.addView(initModelBtn)

        stopModelBtn = Button(this).apply {
            text = "Unload Model"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC0000"))
            textSize = 12f
            setPadding(16, 12, 16, 12)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
            setOnClickListener { triggerUnloadModel() }
        }
        modelControlRow.addView(stopModelBtn)
        modelLayout.addView(modelControlRow)

        // Model Status Card
        modelStatusCard = TextView(this).apply {
            text = "MODEL STATE: UNLOADED\n[No active engine]"
            textSize = 13f
            setTextColor(Color.parseColor("#FF4444"))
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#2E1B1B"))
            setPadding(20, 20, 20, 20)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            layoutParams = params
        }
        modelLayout.addView(modelStatusCard)

        // Quick Inference Test Panel
        val quickTestTitle = TextView(this).apply {
            text = "Quick Inference Test:"
            textSize = 13f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 8, 0, 8)
        }
        modelLayout.addView(quickTestTitle)

        val quickInputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 8)
            }
            layoutParams = params
        }

        quickPromptEdit = EditText(this).apply {
            hint = "Chiedi qualcosa al modello..."
            textSize = 11f
            setTextColor(Color.parseColor("#555555"))
            setBackgroundColor(Color.parseColor("#1C1C1C"))
            setPadding(16, 12, 16, 12)
            typeface = Typeface.DEFAULT
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = params
            isEnabled = false
        }
        quickInputRow.addView(quickPromptEdit)

        quickRunBtn = Button(this).apply {
            text = "Invia"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#333333"))
            textSize = 11f
            setPadding(16, 12, 16, 12)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = params
            isEnabled = false
            setOnClickListener { executeQuickDirectTest() }
        }
        quickInputRow.addView(quickRunBtn)
        modelLayout.addView(quickInputRow)

        // Monospaced output logs for quick direct test
        quickLogTextView = TextView(this).apply {
            textSize = 10f
            setTextColor(Color.parseColor("#00FF66"))
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            setPadding(16, 16, 16, 16)
            typeface = Typeface.MONOSPACE
            text = "Inizializza un modello per avviare un test diretto di inferenza."
        }
        quickLogScrollView = ScrollView(this).apply {
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f)
            layoutParams = params
            addView(quickLogTextView)
        }
        modelLayout.addView(quickLogScrollView)

        contentLayout.addView(modelLayout)

        // --- SECTION B: Tab 2 (API Client Test Suite) ---
        testLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 32, 40, 32)
            visibility = View.GONE // Hidden initially
        }

        // Test Harness Title
        val testTitle = TextView(this).apply {
            text = "Dynamic API Client Harness"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        testLayout.addView(testTitle)

        // Dynamic API selectors (OpenAI vs Ollama)
        val protocolRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }

        openAiPill = Button(this).apply {
            text = "OpenAI API"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3366BB")) // Active initially
            textSize = 12f
            setPadding(24, 12, 24, 12)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 16, 0)
            }
            layoutParams = params
            setOnClickListener { setProtocolMode("OpenAI") }
        }
        protocolRow.addView(openAiPill)

        ollamaPill = Button(this).apply {
            text = "Ollama API"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 12f
            setPadding(24, 12, 24, 12)
            setOnClickListener { setProtocolMode("Ollama") }
        }
        protocolRow.addView(ollamaPill)
        testLayout.addView(protocolRow)

        // Endpoint Selectors Pills Row
        val endpointLabel = TextView(this).apply {
            text = "Select Endpoint to Test:"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 0, 0, 8)
        }
        testLayout.addView(endpointLabel)

        val endpointPillRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        epPill1 = Button(this).apply {
            text = "GET /health"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3366BB")) // Selected initially
            textSize = 10f
            setPadding(12, 8, 12, 8)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = params
            setOnClickListener { setEndpointSelection(0) }
        }
        endpointPillRow.addView(epPill1)

        epPill2 = Button(this).apply {
            text = "GET /v1/models"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 10f
            setPadding(12, 8, 12, 8)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = params
            setOnClickListener { setEndpointSelection(1) }
        }
        endpointPillRow.addView(epPill2)

        epPill3 = Button(this).apply {
            text = "POST /v1/chat"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#222222"))
            textSize = 10f
            setPadding(12, 8, 12, 8)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
            layoutParams = params
            setOnClickListener { setEndpointSelection(2) }
        }
        endpointPillRow.addView(epPill3)
        testLayout.addView(endpointPillRow)

        // Monospaced Interactive Payload Editor
        val editorLabel = TextView(this).apply {
            text = "Request Body Payload (JSON):"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 16, 0, 8)
        }
        testLayout.addView(editorLabel)

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
            setText("[GET Request - No Request Body Required]")
        }
        testLayout.addView(payloadEditor)

        // Run Test Trigger Button
        runTestBtn = Button(this).apply {
            text = "Run API Test"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#22AA55")) // Green accent
            setPadding(24, 16, 24, 16)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 16)
            }
            layoutParams = params
            setOnClickListener { executeClientApiTest() }
        }
        testLayout.addView(runTestBtn)

        // Diagnostics Metrics Panel (Latency, Status Code, Response Size Card row)
        val metricsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        // RTT Latency Box
        val latencyBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1C1C1C"))
            setPadding(12, 12, 12, 12)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 12, 0)
            }
            layoutParams = params
        }
        val latencyTitle = TextView(this).apply {
            text = "LATENCY"
            textSize = 9f
            setTextColor(Color.parseColor("#888888"))
        }
        latencyMetric = TextView(this).apply {
            text = "---"
            textSize = 13f
            setTextColor(Color.parseColor("#00DDFF"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        latencyBox.addView(latencyTitle)
        latencyBox.addView(latencyMetric)
        metricsRow.addView(latencyBox)

        // Status Code Box
        val statusBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1C1C1C"))
            setPadding(12, 12, 12, 12)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 12, 0)
            }
            layoutParams = params
        }
        val statusTitle = TextView(this).apply {
            text = "HTTP STATUS"
            textSize = 9f
            setTextColor(Color.parseColor("#888888"))
        }
        statusMetric = TextView(this).apply {
            text = "---"
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        statusBox.addView(statusTitle)
        statusBox.addView(statusMetric)
        metricsRow.addView(statusBox)

        // Size Box
        val sizeBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1C1C1C"))
            setPadding(12, 12, 12, 12)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
        }
        val sizeTitle = TextView(this).apply {
            text = "RESPONSE SIZE"
            textSize = 9f
            setTextColor(Color.parseColor("#888888"))
        }
        sizeMetric = TextView(this).apply {
            text = "---"
            textSize = 13f
            setTextColor(Color.parseColor("#E0E0E0"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        sizeBox.addView(sizeTitle)
        sizeBox.addView(sizeMetric)
        metricsRow.addView(sizeBox)

        testLayout.addView(metricsRow)

        // Monospaced Client Response Console Scroll View
        val responseConsoleTitle = TextView(this).apply {
            text = "API Response Logs:"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
        }
        testLayout.addView(responseConsoleTitle)

        logTextViewTest = TextView(this).apply {
            textSize = 10f
            setTextColor(Color.parseColor("#00FF66"))
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            setPadding(16, 16, 16, 16)
            typeface = Typeface.MONOSPACE
            text = "Ready to execute local client diagnostic checks."
        }
        logScrollViewTest = ScrollView(this).apply {
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f).apply {
                setMargins(0, 8, 0, 0)
            }
            layoutParams = params
            addView(logTextViewTest)
        }
        testLayout.addView(logScrollViewTest)

        contentLayout.addView(testLayout)
        rootLayout.addView(contentLayout)

        // --- SECTION C: Bottom Tab Bar (Tab Navigation Controls) ---
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            setPadding(16, 16, 16, 16)
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = params
        }

        // Tab Button 1
        serverTabBtn = Button(this).apply {
            text = "Server Daemon"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3366BB")) // Highlighted initially
            textSize = 11f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 6, 0)
            }
            layoutParams = params
            setOnClickListener { switchTab(0) }
        }
        tabBar.addView(serverTabBtn)

        // Tab Button 2 (Model Manager)
        modelTabBtn = Button(this).apply {
            text = "Model Manager"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#121212"))
            textSize = 11f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 6, 0)
            }
            layoutParams = params
            setOnClickListener { switchTab(1) }
        }
        tabBar.addView(modelTabBtn)

        // Tab Button 3 (Test Harness)
        testTabBtn = Button(this).apply {
            text = "Test Harness"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#121212"))
            textSize = 11f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
            setOnClickListener { switchTab(2) }
        }
        tabBar.addView(testTabBtn)
        rootLayout.addView(tabBar)

        setContentView(rootLayout)

        // Attach listener to capture Ktor events in server console view in real-time
        ServerConsole.logListener = { logLine ->
            runOnUiThread {
                if (logLine == "CLEAR_LOGS") {
                    logTextViewServer.text = ""
                } else {
                    logTextViewServer.append("\n" + logLine)
                    logScrollViewServer.post {
                        logScrollViewServer.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }

        refreshConsoleLogs()
    }

    override fun onResume() {
        super.onResume()
        statusUpdateHandler.post(statusUpdateRunnable)
        refreshIpAddress()
        refreshDiscoveredModels()
        updateModelUiState()
    }

    override fun onPause() {
        super.onPause()
        statusUpdateHandler.removeCallbacks(statusUpdateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        ServerConsole.logListener = null
    }

    private fun switchTab(tabIndex: Int) {
        serverLayout.visibility = View.GONE
        modelLayout.visibility = View.GONE
        testLayout.visibility = View.GONE
        
        serverTabBtn.setBackgroundColor(Color.parseColor("#121212"))
        serverTabBtn.setTextColor(Color.parseColor("#888888"))
        modelTabBtn.setBackgroundColor(Color.parseColor("#121212"))
        modelTabBtn.setTextColor(Color.parseColor("#888888"))
        testTabBtn.setBackgroundColor(Color.parseColor("#121212"))
        testTabBtn.setTextColor(Color.parseColor("#888888"))
        
        when (tabIndex) {
            0 -> {
                serverLayout.visibility = View.VISIBLE
                serverTabBtn.setBackgroundColor(Color.parseColor("#3366BB"))
                serverTabBtn.setTextColor(Color.WHITE)
            }
            1 -> {
                modelLayout.visibility = View.VISIBLE
                modelTabBtn.setBackgroundColor(Color.parseColor("#3366BB"))
                modelTabBtn.setTextColor(Color.WHITE)
                refreshDiscoveredModels()
            }
            2 -> {
                testLayout.visibility = View.VISIBLE
                testTabBtn.setBackgroundColor(Color.parseColor("#3366BB"))
                testTabBtn.setTextColor(Color.WHITE)
            }
        }
    }

    private fun setModelMode(isMock: Boolean) {
        if (isModelMockModeSelected == isMock) return
        isModelMockModeSelected = isMock
        
        mockPillModel.setBackgroundColor(Color.parseColor(if (isMock) "#3366BB" else "#222222"))
        mockPillModel.setTextColor(Color.parseColor(if (isMock) "#FFFFFF" else "#888888"))
        realPillModel.setBackgroundColor(Color.parseColor(if (!isMock) "#3366BB" else "#222222"))
        realPillModel.setTextColor(Color.parseColor(if (!isMock) "#FFFFFF" else "#888888"))
        
        modelSpinner.isEnabled = !isMock && hasStoragePermission() && !ModelManager.isLoading && !ModelManager.isModelLoaded
        
        updateModelUiState()
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        ServerConsole.log("Requesting All Files Access permission...")
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

    private fun refreshDiscoveredModels() {
        if (!hasStoragePermission()) {
            permissionStatusCard.text = "STORAGE ACCESS: PERMISSION REQUIRED\n(Grant 'All Files Access' to scan /sdcard/Download/)"
            permissionStatusCard.setTextColor(Color.parseColor("#FFBB33"))
            permissionStatusCard.setBackgroundColor(Color.parseColor("#2E251B"))
            grantPermissionBtn.visibility = View.VISIBLE
            modelSpinner.isEnabled = false
            return
        }

        permissionStatusCard.text = "STORAGE ACCESS: GRANTED\n(Active folder: /sdcard/Download/llm-server/models/)"
        permissionStatusCard.setTextColor(Color.parseColor("#33FF33"))
        permissionStatusCard.setBackgroundColor(Color.parseColor("#1B2E1B"))
        grantPermissionBtn.visibility = View.GONE
        modelSpinner.isEnabled = !isModelMockModeSelected && !ModelManager.isLoading && !ModelManager.isModelLoaded

        discoveredModelFiles = ModelManager.listLocalModels()
        val fileNames = discoveredModelFiles.map { it.name }
        
        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            if (fileNames.isEmpty()) listOf("No .litertlm files found in Downloads") else fileNames
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        modelSpinner.adapter = adapter
    }

    private fun triggerInitializeModel() {
        val isMock = isModelMockModeSelected
        var path: String? = null
        
        if (!isMock) {
            if (discoveredModelFiles.isEmpty()) {
                ServerConsole.log("Model Manager: Cannot initialize real model because no .litertlm files were discovered.")
                return
            }
            val selectedIndex = modelSpinner.selectedItemPosition
            val selectedFile = discoveredModelFiles.getOrNull(selectedIndex)
            if (selectedFile == null) {
                ServerConsole.log("Model Manager: No model selected.")
                return
            }
            path = selectedFile.absolutePath
        }
        
        ServerConsole.log("Model Manager: Initializing model (isMock=$isMock, path=${path ?: "N/A"})...")
        
        modelStatusCard.text = "MODEL STATE: LOADING...\n(Initializing engine, please wait...)"
        modelStatusCard.setTextColor(Color.parseColor("#FFBB33"))
        modelStatusCard.setBackgroundColor(Color.parseColor("#2E251B"))
        initModelBtn.isEnabled = false
        stopModelBtn.isEnabled = false
        mockPillModel.isEnabled = false
        realPillModel.isEnabled = false
        modelSpinner.isEnabled = false
        
        Thread {
            try {
                kotlinx.coroutines.runBlocking {
                    ModelManager.loadModel(path, isMock)
                }
                runOnUiThread {
                    ServerConsole.log("Model Manager: Initialization completed successfully.")
                    updateModelUiState()
                }
            } catch (e: Exception) {
                val error = e.message ?: e.toString()
                runOnUiThread {
                    ServerConsole.log("Model Manager: Initialization failed: $error")
                    updateModelUiState()
                }
            }
        }.start()
    }

    private fun triggerUnloadModel() {
        Thread {
            ModelManager.unloadActiveModel()
            runOnUiThread {
                ServerConsole.log("Model Manager: Model unloaded successfully.")
                updateModelUiState()
            }
        }.start()
    }

    private fun updateModelUiState() {
        val loaded = ModelManager.isModelLoaded
        val loading = ModelManager.isLoading
        val error = ModelManager.loadingError
        val mock = ModelManager.isMockMode
        
        if (loading) {
            modelStatusCard.text = "MODEL STATE: LOADING...\n[Initializing Engine]"
            modelStatusCard.setTextColor(Color.parseColor("#FFBB33"))
            modelStatusCard.setBackgroundColor(Color.parseColor("#2E251B"))
            
            initModelBtn.isEnabled = false
            stopModelBtn.isEnabled = false
            modelSpinner.isEnabled = false
            mockPillModel.isEnabled = false
            realPillModel.isEnabled = false
        } else if (loaded) {
            val details = if (mock) "Mock Mode Active" else "Loaded: ${ModelManager.activeModelName}"
            modelStatusCard.text = "MODEL STATE: ACTIVE\n[$details]"
            modelStatusCard.setTextColor(Color.parseColor("#33FF33"))
            modelStatusCard.setBackgroundColor(Color.parseColor("#1B2E1B"))
            
            initModelBtn.isEnabled = false
            stopModelBtn.isEnabled = true
            
            modelSpinner.isEnabled = false
            mockPillModel.isEnabled = false
            realPillModel.isEnabled = false
            
            quickPromptEdit.isEnabled = true
            quickPromptEdit.setBackgroundColor(Color.parseColor("#0A0A0A"))
            quickPromptEdit.setTextColor(Color.parseColor("#00FF66"))
            quickRunBtn.isEnabled = true
            quickRunBtn.setBackgroundColor(Color.parseColor("#22AA55"))
        } else {
            val errorText = if (error != null) "\nError: $error" else "\n[No active engine]"
            modelStatusCard.text = "MODEL STATE: UNLOADED$errorText"
            modelStatusCard.setTextColor(Color.parseColor("#FF4444"))
            modelStatusCard.setBackgroundColor(Color.parseColor("#2E1B1B"))
            
            initModelBtn.isEnabled = true
            stopModelBtn.isEnabled = false
            
            mockPillModel.isEnabled = true
            realPillModel.isEnabled = true
            modelSpinner.isEnabled = hasStoragePermission() && !isModelMockModeSelected
            
            quickPromptEdit.isEnabled = false
            quickPromptEdit.setBackgroundColor(Color.parseColor("#1C1C1C"))
            quickPromptEdit.setTextColor(Color.parseColor("#555555"))
            quickRunBtn.isEnabled = false
            quickRunBtn.setBackgroundColor(Color.parseColor("#333333"))
        }
    }

    private fun executeQuickDirectTest() {
        val prompt = quickPromptEdit.text.toString()
        if (prompt.isEmpty()) return
        
        val activeProvider = ModelManager.activeProvider
        quickLogTextView.text = ">>> Direct Prompt: \"$prompt\"\n<<< Awaiting tokens...\n"
        quickRunBtn.isEnabled = false
        
        Thread {
            try {
                kotlinx.coroutines.runBlocking {
                    activeProvider.generateStream(prompt).collect { token ->
                        runOnUiThread {
                            quickLogTextView.append(token)
                            quickLogScrollView.post {
                                quickLogScrollView.fullScroll(View.FOCUS_DOWN)
                            }
                        }
                    }
                }
                runOnUiThread {
                    quickRunBtn.isEnabled = true
                }
            } catch (e: Exception) {
                val err = e.message ?: e.toString()
                runOnUiThread {
                    quickLogTextView.append("\n\nCRITICAL INFERENCE ERROR: $err")
                    quickRunBtn.isEnabled = true
                }
            }
        }.start()
    }

    private fun toggleServerDaemon() {
        val intent = Intent(this, LlmServerService::class.java)
        if (LlmServerService.isServiceRunning) {
            ServerConsole.log("Requesting server daemon shutdown...")
            stopService(intent)
        } else {
            val bindHost = when (selectedBindingInterface) {
                "Wi-Fi" -> getInterfaceIp("Wi-Fi")
                "Mobile" -> getInterfaceIp("Mobile")
                else -> "0.0.0.0"
            }
            intent.putExtra("EXTRA_BIND_HOST", bindHost)
            ServerConsole.log("Requesting server daemon startup on $bindHost...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
        updateUiState()
    }

    private fun updateUiState() {
        val running = LlmServerService.isServiceRunning
        if (running) {
            statusCard.text = "SERVER STATE: ACTIVE\n[Ktor Engine Serving Port 8080]"
            statusCard.setTextColor(Color.parseColor("#33FF33"))
            statusCard.setBackgroundColor(Color.parseColor("#1B2E1B"))
            
            toggleButton.text = "Stop Daemon"
            toggleButton.setBackgroundColor(Color.parseColor("#CC0000"))
            
            wifiPill.isEnabled = false
            mobilePill.isEnabled = false
            allPill.isEnabled = false
            wifiPill.alpha = 0.5f
            mobilePill.alpha = 0.5f
            allPill.alpha = 0.5f
        } else {
            statusCard.text = "SERVER STATE: OFFLINE\n[Daemon Inactive]"
            statusCard.setTextColor(Color.parseColor("#FF4444"))
            statusCard.setBackgroundColor(Color.parseColor("#2E1B1B"))
            
            toggleButton.text = "Start Daemon"
            toggleButton.setBackgroundColor(Color.parseColor("#0099CC"))
            
            wifiPill.isEnabled = true
            mobilePill.isEnabled = true
            allPill.isEnabled = true
            wifiPill.alpha = 1.0f
            mobilePill.alpha = 1.0f
            allPill.alpha = 1.0f
        }
    }

    private fun refreshIpAddress() {
        val ip = when (selectedBindingInterface) {
            "Wi-Fi" -> getInterfaceIp("Wi-Fi")
            "Mobile" -> getInterfaceIp("Mobile")
            else -> getLocalIpAddress()
        }
        
        if (ip != "127.0.0.1") {
            ipTextView.text = "LAN Access Endpoint:\nhttp://$ip:8080"
            ipTextView.setTextColor(Color.parseColor("#00DDFF"))
        } else {
            val label = if (selectedBindingInterface == "Wi-Fi") "NO ACTIVE WI-FI IP" else if (selectedBindingInterface == "Mobile") "NO ACTIVE MOBILE NETWORK IP" else "NOT CONNECTED"
            ipTextView.text = "LAN Access Endpoint:\n$label"
            ipTextView.setTextColor(Color.parseColor("#FF8800"))
        }
    }

    private fun refreshConsoleLogs() {
        val cached = ServerConsole.getLogs()
        if (cached.isNotEmpty()) {
            val sb = StringBuilder()
            for (line in cached) {
                sb.append(line).append("\n")
            }
            logTextViewServer.text = sb.toString().trimEnd()
            logScrollViewServer.post {
                logScrollViewServer.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun launchBatterySettings() {
        try {
            ServerConsole.log("Requesting OS Ignore Battery Optimizations Settings...")
            val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            ServerConsole.log("Failed to open battery settings: ${e.message}")
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val inetAddress = addresses.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress ?: ""
                    }
                }
            }
        } catch (ex: Exception) {
            ServerConsole.log("Failed to resolve IP address: ${ex.message}")
        }
        return "127.0.0.1"
    }

    private fun setInterfaceBinding(mode: String) {
        if (selectedBindingInterface == mode) return
        if (LlmServerService.isServiceRunning) {
            ServerConsole.log("Cannot change binding interface while daemon is running.")
            return
        }
        selectedBindingInterface = mode
        
        // Update styling
        wifiPill.setBackgroundColor(Color.parseColor(if (mode == "Wi-Fi") "#3366BB" else "#222222"))
        wifiPill.setTextColor(Color.parseColor(if (mode == "Wi-Fi") "#FFFFFF" else "#888888"))
        
        mobilePill.setBackgroundColor(Color.parseColor(if (mode == "Mobile") "#3366BB" else "#222222"))
        mobilePill.setTextColor(Color.parseColor(if (mode == "Mobile") "#FFFFFF" else "#888888"))
        
        allPill.setBackgroundColor(Color.parseColor(if (mode == "All") "#3366BB" else "#222222"))
        allPill.setTextColor(Color.parseColor(if (mode == "All") "#FFFFFF" else "#888888"))
        
        refreshIpAddress()
    }

    private fun getInterfaceIp(type: String): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val name = networkInterface.name.lowercase()
                
                // Filter by name
                if (type == "Wi-Fi" && !name.contains("wlan")) continue
                if (type == "Mobile" && !(name.contains("rmnet") || name.contains("ccmni") || name.contains("ppp") || name.contains("wwan") || name.contains("epdg"))) continue
                
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val inetAddress = addresses.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress ?: ""
                    }
                }
            }
        } catch (ex: Exception) {
            ServerConsole.log("Failed to resolve $type IP: ${ex.message}")
        }
        return "127.0.0.1"
    }

    // --- Dynamic API Client Test Harness Logic ---

    private fun setProtocolMode(mode: String) {
        if (activeProtocol == mode) return
        activeProtocol = mode
        
        if (mode == "OpenAI") {
            openAiPill.setBackgroundColor(Color.parseColor("#3366BB"))
            openAiPill.setTextColor(Color.WHITE)
            ollamaPill.setBackgroundColor(Color.parseColor("#222222"))
            ollamaPill.setTextColor(Color.parseColor("#888888"))
            
            // OpenAI endpoints
            epPill1.text = "GET /health"
            epPill2.text = "GET /v1/models"
            epPill3.text = "POST /v1/chat"
            epPill3.visibility = View.VISIBLE
        } else {
            openAiPill.setBackgroundColor(Color.parseColor("#222222"))
            openAiPill.setTextColor(Color.parseColor("#888888"))
            ollamaPill.setBackgroundColor(Color.parseColor("#3366BB"))
            ollamaPill.setTextColor(Color.WHITE)
            
            // Ollama endpoints
            epPill1.text = "GET /api/tags"
            epPill2.text = "POST /api/chat"
            epPill3.visibility = View.GONE // Ollama has only 2 endpoints configured in this mock
        }
        
        // Reset selected endpoint to 0 upon switching protocol modes
        setEndpointSelection(0)
    }

    private fun setEndpointSelection(index: Int) {
        selectedEndpointIndex = index
        
        // Reset pill styles
        epPill1.setBackgroundColor(Color.parseColor("#222222"))
        epPill1.setTextColor(Color.parseColor("#888888"))
        epPill2.setBackgroundColor(Color.parseColor("#222222"))
        epPill2.setTextColor(Color.parseColor("#888888"))
        epPill3.setBackgroundColor(Color.parseColor("#222222"))
        epPill3.setTextColor(Color.parseColor("#888888"))
        
        when (index) {
            0 -> {
                epPill1.setBackgroundColor(Color.parseColor("#3366BB"))
                epPill1.setTextColor(Color.WHITE)
            }
            1 -> {
                epPill2.setBackgroundColor(Color.parseColor("#3366BB"))
                epPill2.setTextColor(Color.WHITE)
            }
            2 -> {
                epPill3.setBackgroundColor(Color.parseColor("#3366BB"))
                epPill3.setTextColor(Color.WHITE)
            }
        }
        
        // Repopulate payload editor and handle visibility/activity
        refreshPayloadEditor()
    }

    private fun refreshPayloadEditor() {
        if (activeProtocol == "OpenAI") {
            when (selectedEndpointIndex) {
                0, 1 -> {
                    payloadEditor.setText("[GET Request - No Request Body Required]")
                    payloadEditor.isEnabled = false
                    payloadEditor.setBackgroundColor(Color.parseColor("#1C1C1C"))
                    payloadEditor.setTextColor(Color.parseColor("#555555"))
                }
                2 -> {
                    payloadEditor.setText("{\n  \"model\": \"meta-llama-3-8b-instruct\",\n  \"messages\": [\n    {\n      \"role\": \"user\",\n      \"content\": \"Hello local LLM!\"\n    }\n  ],\n  \"temperature\": 0.7\n}")
                    payloadEditor.isEnabled = true
                    payloadEditor.setBackgroundColor(Color.parseColor("#0A0A0A"))
                    payloadEditor.setTextColor(Color.parseColor("#00FF66"))
                }
            }
        } else {
            when (selectedEndpointIndex) {
                0 -> {
                    payloadEditor.setText("[GET Request - No Request Body Required]")
                    payloadEditor.isEnabled = false
                    payloadEditor.setBackgroundColor(Color.parseColor("#1C1C1C"))
                    payloadEditor.setTextColor(Color.parseColor("#555555"))
                }
                1 -> {
                    payloadEditor.setText("{\n  \"model\": \"llama3\",\n  \"messages\": [\n    {\n      \"role\": \"user\",\n      \"content\": \"why is the sky blue?\"\n    }\n  ],\n  \"stream\": false\n}")
                    payloadEditor.isEnabled = true
                    payloadEditor.setBackgroundColor(Color.parseColor("#0A0A0A"))
                    payloadEditor.setTextColor(Color.parseColor("#00FF66"))
                }
            }
        }
    }

    private fun executeClientApiTest() {
        // Resolve method and route
        var method = "GET"
        var path = "/health"
        var bodyPayload: String? = null
        
        if (activeProtocol == "OpenAI") {
            when (selectedEndpointIndex) {
                0 -> { method = "GET"; path = "/health" }
                1 -> { method = "GET"; path = "/v1/models" }
                2 -> { method = "POST"; path = "/v1/chat/completions"; bodyPayload = payloadEditor.text.toString() }
            }
        } else {
            when (selectedEndpointIndex) {
                0 -> { method = "GET"; path = "/api/tags" }
                1 -> { method = "POST"; path = "/api/chat"; bodyPayload = payloadEditor.text.toString() }
            }
        }
        
        // Resolve target Ktor listening address
        val activeHost = if (LlmServerService.isServiceRunning) {
            val h = LlmServerService.activeBindHost
            if (h == "0.0.0.0") "localhost" else h
        } else {
            "localhost"
        }
        
        ServerConsole.log("[Client Harness] Executing $method request to http://$activeHost:8080$path")
        
        val body = bodyPayload

        // Format beautiful raw HTTP request dump frame
        val requestDump = StringBuilder().apply {
            append(">>> HTTP REQUEST DUMP:\n")
            append("$method $path HTTP/1.1\n")
            append("Host: $activeHost:8080\n")
            append("Content-Type: application/json\n")
            append("Authorization: Bearer mock-token\n")
            if (method == "POST" && body != null) {
                append("Content-Length: ${body.encodeToByteArray().size}\n")
                append("\n")
                append(body)
            } else {
                append("\n[GET Request - No Request Body Payload]")
            }
            append("\n\n----------------------------------------\n\n")
        }.toString()
        
        // Visual indicator for launch state
        logTextViewTest.text = requestDump + "Polling local edge AI server daemon..."
        latencyMetric.text = "---"
        statusMetric.text = "..."
        statusMetric.setTextColor(Color.WHITE)
        sizeMetric.text = "---"
        
        Thread {
            val startTime = System.currentTimeMillis()
            var responseCode = -1
            var responseBody = ""
            var ok = false
            
            try {
                val url = URL("http://$activeHost:8080$path")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.connectTimeout = 3000
                conn.readTimeout = 4000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer mock-token")
                
                if (method == "POST" && body != null) {
                    conn.doOutput = true
                    val bytes = body.encodeToByteArray()
                    conn.setFixedLengthStreamingMode(bytes.size)
                    val os: OutputStream = conn.outputStream
                    os.write(bytes)
                    os.flush()
                    os.close()
                }
                
                responseCode = conn.responseCode
                ok = responseCode in 200..299
                
                val stream = if (ok) conn.inputStream else conn.errorStream
                if (stream != null) {
                    responseBody = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                }
            } catch (e: Exception) {
                responseBody = "API CONNECTION ERROR:\n${e.message}\n\nIs the local daemon server running? Tap 'Server Daemon' tab at the bottom and confirm the server is ACTIVE."
                ServerConsole.log("[Client Harness] Diagnostic execution failed: ${e.message}")
            }
            
            val latency = System.currentTimeMillis() - startTime
            val finalResponseCode = responseCode
            val finalResponseBody = responseBody
            val finalOk = ok
            
            // Format dynamic response received log dump
            val responseText = StringBuilder().apply {
                append(requestDump)
                append("<<< RESPONSE RECEIVED:\n")
                if (finalResponseCode != -1) {
                    append("HTTP/1.1 $finalResponseCode ${if (finalOk) "OK" else "ERROR"}\n")
                    append("Content-Length: ${finalResponseBody.encodeToByteArray().size}\n")
                    append("\n")
                    append(finalResponseBody)
                } else {
                    append("API CONNECTION ERROR:\n")
                    append(finalResponseBody)
                }
            }.toString()
            
            runOnUiThread {
                latencyMetric.text = "${latency}ms"
                statusMetric.text = if (finalResponseCode != -1) "$finalResponseCode" else "ERR"
                statusMetric.setTextColor(if (finalOk) Color.parseColor("#00FF66") else Color.parseColor("#FF4444"))
                sizeMetric.text = "${finalResponseBody.length} chars"
                
                logTextViewTest.text = responseText
                logScrollViewTest.post {
                    logScrollViewTest.fullScroll(View.FOCUS_DOWN)
                }
            }
        }.start()
    }
}
