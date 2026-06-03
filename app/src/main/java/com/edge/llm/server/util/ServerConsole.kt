package com.edge.llm.server.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LogCategory: Distinguishes log origins for custom filtering inside the consolidated console view.
 */
enum class LogCategory {
    SERVER,
    ENGINE,
    UI
}

/**
 * LogEntry: Represents a single log event with timestamp, category, and text data.
 */
data class LogEntry(
    val timestamp: String,
    val category: LogCategory,
    val message: String
) {
    fun toFormattedString(): String = "[$timestamp] [${category.name}] $message"
}

/**
 * ServerConsole: A thread-safe global singleton that stores a sliding log history of server
 * activities and notifies listeners dynamically.
 */
object ServerConsole {
    private const val MAX_LOG_LINES = 200
    private val logs = mutableListOf<LogEntry>()
    
    // Thread-safe listener for new log notifications
    @Volatile
    var logListener: ((LogEntry) -> Unit)? = null

    /**
     * Appends a log line with a specific category.
     */
    @Synchronized
    fun log(category: LogCategory, message: String) {
        val timeString = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val entry = LogEntry(timeString, category, message)
        
        logs.add(entry)
        if (logs.size > MAX_LOG_LINES) {
            logs.removeAt(0)
        }
        
        // Notify listener
        logListener?.invoke(entry)
    }

    /**
     * Backward-compatible logger that auto-detects categories based on keywords.
     */
    @Synchronized
    fun log(message: String) {
        val category = when {
            message.contains("-->") || message.contains("<--") || message.contains("Ktor") || message.contains("Server") || message.contains("Client") || message.contains("HTTP") -> LogCategory.SERVER
            message.contains("Model") || message.contains("LiteRT") || message.contains("Inference") || message.contains("Engine") || message.contains("provider") -> LogCategory.ENGINE
            else -> LogCategory.UI
        }
        log(category, message)
    }

    /**
     * Retrieves the current log buffer, optionally filtered by category.
     */
    @Synchronized
    fun getLogs(category: LogCategory? = null): List<LogEntry> {
        return if (category == null) {
            ArrayList(logs)
        } else {
            logs.filter { it.category == category }
        }
    }

    /**
     * Clears the console logs.
     */
    @Synchronized
    fun clear() {
        logs.clear()
        logListener?.invoke(LogEntry("", LogCategory.UI, "CLEAR_LOGS"))
    }
}
