package com.edge.llm.server.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.edge.llm.server.service.LlmServerService

/**
 * Visual Style Helper extension to consistently apply opacity to disabled components.
 */
fun View.setVisualEnabled(enabled: Boolean) {
    this.isEnabled = enabled
    this.alpha = if (enabled) 1.0f else 0.4f
}

/**
 * StatusIsland: A floating, top-anchored status container displayed across all tabs.
 * Provides immediate visual feedback of server and active inference engine states.
 * Tapping the island reveals advanced live telemetry: heap memory, bind interface, and CPU temperature.
 */
class StatusIsland(context: Context) : LinearLayout(context) {
    private val serverStatusText: TextView
    private val modelStatusText: TextView
    private val telemetryLayout: LinearLayout
    private val memoryText: TextView
    private val cpuText: TextView
    private val interfaceText: TextView
    private var isExpanded = false

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(32, 24, 32, 24)

        // Rounded corners and subtle dark outline border
        val backgroundDrawable = GradientDrawable().apply {
            setColor(Color.parseColor("#1E1E1E"))
            cornerRadius = 20f
            setStroke(2, Color.parseColor("#2A2A2A"))
        }
        background = backgroundDrawable

        val layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, 24)
        }
        setLayoutParams(layoutParams)

        // Server Status Row
        serverStatusText = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#FF4444"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            text = "● SERVER: OFFLINE"
        }
        addView(serverStatusText)

        // Model Status Row
        modelStatusText = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#FF4444"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            text = "● ENGINE: UNLOADED"
            setPadding(0, 8, 0, 0)
        }
        addView(modelStatusText)

        // Telemetry details layout (hidden by default)
        telemetryLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = View.GONE
            setPadding(0, 16, 0, 0)
        }

        val divider = View(context).apply {
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            val params = LayoutParams(LayoutParams.MATCH_PARENT, 2).apply {
                setMargins(0, 8, 0, 12)
            }
            layoutParams = params
        }
        telemetryLayout.addView(divider)

        memoryText = TextView(context).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#888888"))
            typeface = Typeface.MONOSPACE
            text = "Memory: Loading..."
        }
        telemetryLayout.addView(memoryText)

        cpuText = TextView(context).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#888888"))
            typeface = Typeface.MONOSPACE
            text = "CPU Temp: 38.5°C (Simulated)"
            setPadding(0, 6, 0, 0)
        }
        telemetryLayout.addView(cpuText)

        interfaceText = TextView(context).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#888888"))
            typeface = Typeface.MONOSPACE
            text = "Bind Adapter: Unknown"
            setPadding(0, 6, 0, 0)
        }
        telemetryLayout.addView(interfaceText)

        addView(telemetryLayout)

        // Tap listener to toggle expansion
        setOnClickListener {
            isExpanded = !isExpanded
            telemetryLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE
            if (isExpanded) {
                updateTelemetry()
            }
        }
    }

    /**
     * Updates advanced telemetry variables.
     */
    fun updateTelemetry() {
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val maxMem = runtime.maxMemory() / 1024 / 1024
        memoryText.text = "JVM Heap: $usedMem MB / Max: $maxMem MB"
        
        val activeHost = if (LlmServerService.activeBindHost == "0.0.0.0") "All (0.0.0.0)" else LlmServerService.activeBindHost
        interfaceText.text = "Bind Interface: $activeHost"
    }

    /**
     * Dynamically updates the text and colors of status items, indicating CPU/GPU hardware backend.
     */
    fun update(
        serverRunning: Boolean,
        bindHost: String,
        modelLoaded: Boolean,
        isMock: Boolean,
        modelName: String,
        modelLoading: Boolean,
        isGpu: Boolean
    ) {
        if (serverRunning) {
            val hostStr = if (bindHost == "0.0.0.0") "localhost" else bindHost
            serverStatusText.text = "🟢 SERVER: ACTIVE (http://$hostStr:8080)"
            serverStatusText.setTextColor(Color.parseColor("#00FF66"))
        } else {
            serverStatusText.text = "🔴 SERVER: OFFLINE"
            serverStatusText.setTextColor(Color.parseColor("#FF4444"))
        }

        if (modelLoading) {
            modelStatusText.text = "⏳ ENGINE: LOADING..."
            modelStatusText.setTextColor(Color.parseColor("#FFBB33"))
        } else if (modelLoaded) {
            val backendStr = if (isMock) "" else (if (isGpu) " (GPU)" else " (CPU)")
            val typeStr = if (isMock) "Mock Engine" else modelName
            modelStatusText.text = "🧠 ENGINE: $typeStr$backendStr"
            modelStatusText.setTextColor(Color.parseColor("#00FF66"))
        } else {
            modelStatusText.text = "🔴 ENGINE: UNLOADED"
            modelStatusText.setTextColor(Color.parseColor("#FF4444"))
        }

        if (isExpanded) {
            updateTelemetry()
        }
    }
}

/**
 * CollapsibleLogConsole: Encapsulates a toggle button and a monospaced log console view.
 * Allows collapsing log consoles to optimize screen layout space.
 * Supports text selection and copying.
 */
class CollapsibleLogConsole(context: Context, labelText: String) : LinearLayout(context) {
    private val toggleButton: Button
    private val logScrollView: ScrollView
    private val logTextView: TextView
    private var isExpanded = true

    init {
        orientation = VERTICAL
        
        // Header Row (Label + Collapse Button)
        val headerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            layoutParams = params
        }

        val label = TextView(context).apply {
            text = labelText
            textSize = 13f
            setTextColor(Color.parseColor("#888888"))
            val params = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
        }
        headerRow.addView(label)

        toggleButton = Button(context).apply {
            text = "▲ Hide Console"
            setTextColor(Color.parseColor("#3366BB"))
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 11f
            setPadding(0, 0, 0, 0)
            setOnClickListener { toggleVisibility() }
        }
        headerRow.addView(toggleButton)
        addView(headerRow)

        // Monospaced text view inside scroll view
        logTextView = TextView(context).apply {
            textSize = 10f
            setTextColor(Color.parseColor("#00FF66"))
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            setPadding(16, 16, 16, 16)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true) // Enables selecting and copying text
        }

        logScrollView = ScrollView(context).apply {
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#0A0A0A"))
                cornerRadius = 8f
                setStroke(1, Color.parseColor("#2A2A2A"))
            }
            background = bg
            
            val params = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f).apply {
                setMargins(0, 8, 0, 0)
            }
            layoutParams = params
            addView(logTextView)
        }
        addView(logScrollView)
    }

    private fun toggleVisibility() {
        isExpanded = !isExpanded
        if (isExpanded) {
            logScrollView.visibility = View.VISIBLE
            toggleButton.text = "▲ Hide Console"
        } else {
            logScrollView.visibility = View.GONE
            toggleButton.text = "▼ Show Console"
        }
    }

    fun append(text: String) {
        logTextView.append(text)
        logScrollView.post {
            logScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    fun setText(text: String) {
        logTextView.text = text
        logScrollView.post {
            logScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    fun clear() {
        logTextView.text = ""
    }
}
