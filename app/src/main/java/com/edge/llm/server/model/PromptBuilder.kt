package com.edge.llm.server.model

/**
 * Common internal representation of a chat message.
 */
data class ChatMessage(
    val role: String,
    val content: String
)

/**
 * Pure Kotlin prompt builder for Gemma-style chat formatting.
 * Keeps zero Android runtime dependencies so it can be verified via standard JVM unit tests.
 */
object PromptBuilder {
    const val START_TURN = "<start_of_turn>"
    const val END_TURN = "<end_of_turn>"
    const val ROLE_USER = "user"
    const val ROLE_MODEL = "model"
    const val ROLE_ASSISTANT = "assistant"
    const val ROLE_SYSTEM = "system"
    const val ROLE_TOOL = "tool"

    /**
     * Formats an ordered list of chat messages into a single Gemma turn-templated prompt.
     * 
     * Output format follows Google Gemma specifications:
     * <start_of_turn>user
     * {system_instructions}\n\n{user_message}<end_of_turn>
     * <start_of_turn>model
     * {model_response}<end_of_turn>
     * ...
     * <start_of_turn>model\n
     */
    fun buildGemmaPrompt(messages: List<ChatMessage>): String {
        if (messages.isEmpty()) {
            return "$START_TURN$ROLE_MODEL\n"
        }

        val sb = StringBuilder()

        // Extract system messages to prepend to the first user turn
        val systemMessages = messages.filter { it.role.equals(ROLE_SYSTEM, ignoreCase = true) }
        val nonSystemMessages = messages.filter { !it.role.equals(ROLE_SYSTEM, ignoreCase = true) }

        val systemPrefix = if (systemMessages.isNotEmpty()) {
            systemMessages.joinToString("\n\n") { it.content.trim() }.trim()
        } else {
            null
        }

        var systemInjected = false

        if (nonSystemMessages.isEmpty() && systemPrefix != null) {
            // Only system message provided: wrap in an initial user turn
            sb.append(START_TURN).append(ROLE_USER).append("\n")
            sb.append(systemPrefix)
            sb.append(END_TURN).append("\n")
        } else {
            for (msg in nonSystemMessages) {
                val rawRole = msg.role.trim().lowercase()
                val rawContent = msg.content.trim()

                when (rawRole) {
                    ROLE_USER -> {
                        sb.append(START_TURN).append(ROLE_USER).append("\n")
                        if (!systemInjected && systemPrefix != null) {
                            sb.append(systemPrefix).append("\n\n")
                            systemInjected = true
                        }
                        sb.append(rawContent)
                        sb.append(END_TURN).append("\n")
                    }
                    ROLE_ASSISTANT, ROLE_MODEL -> {
                        sb.append(START_TURN).append(ROLE_MODEL).append("\n")
                        sb.append(rawContent)
                        sb.append(END_TURN).append("\n")
                    }
                    ROLE_TOOL -> {
                        sb.append(START_TURN).append(ROLE_USER).append("\n")
                        sb.append("[Tool Result]: ").append(rawContent)
                        sb.append(END_TURN).append("\n")
                    }
                    else -> {
                        sb.append(START_TURN).append(ROLE_USER).append("\n")
                        sb.append(rawContent)
                        sb.append(END_TURN).append("\n")
                    }
                }
            }

            // Fallback: if system messages were provided but no user turn was present
            if (!systemInjected && systemPrefix != null) {
                sb.insert(0, "$START_TURN$ROLE_USER\n$systemPrefix$END_TURN\n")
            }
        }

        // Add cue for model generation
        sb.append(START_TURN).append(ROLE_MODEL).append("\n")

        return sb.toString()
    }
}
