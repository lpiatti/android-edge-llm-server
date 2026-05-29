package com.edge.llm.server

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ServerConsole: A thread-safe global singleton that stores a sliding log history of server
 * activities (incoming requests, responses, lifecycle states) and notifies listeners dynamically.
 *
 * This enables decoupling the HTTP Server and background thread execution from the Activity,
 * while allowing a simple, clean console log to be displayed on the screen.
 */
object ServerConsole {
    private const val MAX_LOG_LINES = 100
    private val logs = mutableListOf<String>()
    
    // Thread-safe listener for new log notifications
    @Volatile
    var logListener: ((String) -> Unit)? = null

    /**
     * Appends a log line with a formatted timestamp.
     */
    @Synchronized
    fun log(message: String) {
        val timeString = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val formattedLog = "[$timeString] $message"
        
        logs.add(formattedLog)
        if (logs.size > MAX_LOG_LINES) {
            logs.removeAt(0)
        }
        
        // Notify listener (if registered)
        logListener?.invoke(formattedLog)
    }

    /**
     * Retrieves the entire current log buffer.
     */
    @Synchronized
    fun getLogs(): List<String> = ArrayList(logs)

    /**
     * Clears the console logs.
     */
    @Synchronized
    fun clear() {
        logs.clear()
        logListener?.invoke("CLEAR_LOGS")
    }
}
