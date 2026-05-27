package com.edge.llm.server

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * MainActivity: The primary UI and entry point of the application.
 *
 * In accordance with our decoupled architecture (defined in docs/architecture.md),
 * this activity serves purely as a control panel/interface to interact with
 * the background server daemon. It does not contain any inference or server logic.
 *
 * To maintain a clean, zero-complication codebase for learning and development,
 * the layout is built programmatically without complex XML files or external UI frameworks.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Create a root linear layout to hold our UI elements vertically
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212")) // Modern sleek dark theme background
            setPadding(48, 48, 48, 48)
        }

        // 2. Title Text View (Large header)
        val titleText = TextView(this).apply {
            text = "Edge LLM Server"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        // 3. Subtitle / Status Text View
        val subtitleText = TextView(this).apply {
            text = "Android Edge AI local inference daemon"
            textSize = 16f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 64)
        }

        // 4. Server status display box
        val statusCard = TextView(this).apply {
            text = "Server Status: OFFLINE\n(Phase 2 Skeleton Active)"
            textSize = 14f
            setTextColor(Color.parseColor("#FF5555")) // Red color for offline status
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            setPadding(32, 24, 32, 24)
        }

        // 5. Minimal control toggle button
        val toggleButton = Button(this).apply {
            text = "Start Server Daemon"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3366BB")) // Modern cobalt blue button
            setPadding(32, 24, 32, 24)
            
            // Add margin to separate from status card
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 48, 0, 0)
            }
            layoutParams = params

            // Basic click interaction for visual validation
            setOnClickListener {
                if (statusCard.text.toString().contains("OFFLINE")) {
                    statusCard.text = "Server Status: ACTIVE (Fake Endpoints)\nReady to process LLM inference."
                    statusCard.setTextColor(Color.parseColor("#55FF55")) // Green for active status
                    text = "Stop Server Daemon"
                    setBackgroundColor(Color.parseColor("#BB3333")) // Red button
                } else {
                    statusCard.text = "Server Status: OFFLINE\n(Phase 2 Skeleton Active)"
                    statusCard.setTextColor(Color.parseColor("#FF5555"))
                    text = "Start Server Daemon"
                    setBackgroundColor(Color.parseColor("#3366BB"))
                }
            }
        }

        // 6. Add all elements to the root container
        container.addView(titleText)
        container.addView(subtitleText)
        container.addView(statusCard)
        container.addView(toggleButton)

        // 7. Set the created layout as the active screen content
        setContentView(container)
    }
}
