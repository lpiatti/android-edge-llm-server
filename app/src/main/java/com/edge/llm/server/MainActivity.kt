package com.edge.llm.server

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * MainActivity: The primary control panel of our edge LLM server node.
 *
 * In accordance with docs/architecture.md, it is purely a visual control dashboard.
 * It manages the lifecycle of LlmServerService, retrieves local LAN networking configuration,
 * launches OS battery whitelisting screens, and displays server-side console logs in real-time.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusCard: TextView
    private lateinit var toggleButton: Button
    private lateinit var ipTextView: TextView
    private lateinit var logTextView: TextView
    private lateinit var logScrollView: ScrollView

    private val statusUpdateHandler = Handler(Looper.getMainLooper())
    private val statusUpdateRunnable = object : Runnable {
        override fun run() {
            updateUiState()
            statusUpdateHandler.postDelayed(this, 1000) // Poll every 1 second
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Root Vertical Layout with Dark Palette
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212")) // Premium dark mode background
            setPadding(40, 40, 40, 40)
        }

        // 2. App Header Title
        val titleText = TextView(this).apply {
            text = "Edge LLM Server Console"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, 16, 0, 8)
        }
        rootLayout.addView(titleText)

        // 3. Subtitle / Daemon info
        val subtitleText = TextView(this).apply {
            text = "Android Native AI Server Node"
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 32)
        }
        rootLayout.addView(subtitleText)

        // 4. IP / Access Endpoint View Card
        ipTextView = TextView(this).apply {
            text = "LAN Access Endpoint: RESOLVING..."
            textSize = 14f
            setTextColor(Color.parseColor("#33B5E5")) // Elegant cobalt blue
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1C1C1C"))
            setPadding(24, 24, 24, 24)
            typeface = Typeface.MONOSPACE
        }
        rootLayout.addView(ipTextView)

        // 5. Server Status Display Panel
        statusCard = TextView(this).apply {
            text = "SERVER STATE: OFFLINE"
            textSize = 15f
            setTextColor(Color.parseColor("#FF4444")) // Vibrant Red
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(32, 28, 32, 28)
            
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 24, 0, 0)
            }
            layoutParams = params
        }
        rootLayout.addView(statusCard)

        // 6. Horizontal Button Row
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 28, 0, 28)
            }
            layoutParams = params
        }

        // Toggle Server Button
        toggleButton = Button(this).apply {
            text = "Start Daemon"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0099CC")) // Deep blue
            setPadding(24, 20, 24, 20)
            
            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.5f
            ).apply {
                setMargins(0, 0, 16, 0)
            }
            layoutParams = params

            setOnClickListener {
                toggleServerDaemon()
            }
        }
        buttonRow.addView(toggleButton)

        // Battery Optimization Settings Button
        val batteryOptButton = Button(this).apply {
            text = "Battery Optimization"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#444444")) // Gunmetal grey
            setPadding(16, 20, 16, 20)
            
            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            )
            layoutParams = params

            setOnClickListener {
                launchBatterySettings()
            }
        }
        buttonRow.addView(batteryOptButton)
        rootLayout.addView(buttonRow)

        // 7. Console Title + Clear Logs Button Row
        val consoleHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = params
        }

        val consoleLabel = TextView(this).apply {
            text = "Live Server Console Logs:"
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            )
            layoutParams = params
        }
        consoleHeader.addView(consoleLabel)

        val clearLogsButton = Button(this).apply {
            text = "Clear"
            setTextColor(Color.parseColor("#FF4444"))
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 11f
            setPadding(16, 0, 16, 0)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = params
            setOnClickListener {
                ServerConsole.clear()
            }
        }
        consoleHeader.addView(clearLogsButton)
        rootLayout.addView(consoleHeader)

        // 8. Monospaced Terminal Console Window
        logTextView = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#00FF66")) // Retro terminal green
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            setPadding(20, 20, 20, 20)
            typeface = Typeface.MONOSPACE
            text = "System ready.\nConsole listening for daemon logs..."
        }

        logScrollView = ScrollView(this).apply {
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f // Fills remaining vertical screen space!
            ).apply {
                setMargins(0, 12, 0, 0)
            }
            layoutParams = params
            addView(logTextView)
        }
        rootLayout.addView(logScrollView)

        setContentView(rootLayout)

        // 9. Attach real-time listener to ServerConsole singleton
        ServerConsole.logListener = { logLine ->
            runOnUiThread {
                if (logLine == "CLEAR_LOGS") {
                    logTextView.text = ""
                } else {
                    logTextView.append("\n" + logLine)
                    logScrollView.post {
                        logScrollView.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }

        // Initialize local logs
        refreshConsoleLogs()
    }

    override fun onResume() {
        super.onResume()
        // Start UI state periodic poller
        statusUpdateHandler.post(statusUpdateRunnable)
        refreshIpAddress()
    }

    override fun onPause() {
        super.onPause()
        // Stop UI poller to preserve resources when activity is hidden
        statusUpdateHandler.removeCallbacks(statusUpdateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove listener to prevent memory leaks
        ServerConsole.logListener = null
    }

    private fun toggleServerDaemon() {
        val intent = Intent(this, LlmServerService::class.java)
        if (LlmServerService.isServiceRunning) {
            ServerConsole.log("Requesting server daemon shutdown...")
            stopService(intent)
        } else {
            ServerConsole.log("Requesting server daemon startup...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
        // Force immediate UI update to give snappy click feedback
        updateUiState()
    }

    private fun updateUiState() {
        val running = LlmServerService.isServiceRunning
        
        if (running) {
            statusCard.text = "SERVER STATE: ACTIVE\n[Ktor Engine Serving Port 8080]"
            statusCard.setTextColor(Color.parseColor("#33FF33")) // Vivid Green
            statusCard.setBackgroundColor(Color.parseColor("#1B2E1B"))
            
            toggleButton.text = "Stop Daemon"
            toggleButton.setBackgroundColor(Color.parseColor("#CC0000")) // Deep red stop
        } else {
            statusCard.text = "SERVER STATE: OFFLINE\n[Daemon Inactive]"
            statusCard.setTextColor(Color.parseColor("#FF4444")) // Red
            statusCard.setBackgroundColor(Color.parseColor("#2E1B1B"))
            
            toggleButton.text = "Start Daemon"
            toggleButton.setBackgroundColor(Color.parseColor("#0099CC")) // Cobalt blue
        }
    }

    private fun refreshIpAddress() {
        val ip = getLocalIpAddress()
        if (ip != "127.0.0.1") {
            ipTextView.text = "LAN Access Endpoint:\nhttp://$ip:8080"
            ipTextView.setTextColor(Color.parseColor("#00DDFF"))
        } else {
            ipTextView.text = "LAN Access Endpoint:\nNOT CONNECTED TO WIFI"
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
            logTextView.text = sb.toString().trimEnd()
            logScrollView.post {
                logScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun launchBatterySettings() {
        try {
            ServerConsole.log("Requesting OS Ignore Battery Optimizations list...")
            val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            ServerConsole.log("Error opening settings: ${e.message}")
        }
    }

    /**
     * Resolves the device's current local IPv4 address across active interfaces.
     */
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
}
