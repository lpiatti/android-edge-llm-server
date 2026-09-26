package com.edge.llm.server.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Server-side enforcement of OpenAI/Ollama `stop` sequences on a streamed text.
 *
 * Text that could be the beginning of a stop sequence is held back until it is proven
 * not to be one, so a client never receives a partial stop sequence.
 */
class StopSequenceFilter(stops: List<String>) {
    private val stops = stops.filter { it.isNotEmpty() }
    private val pending = StringBuilder()

    var stopped = false
        private set

    /** Feeds a chunk; returns the text that is safe to emit now. */
    fun push(chunk: String): String {
        if (stopped) return ""
        if (stops.isEmpty()) return chunk
        pending.append(chunk)

        var hit = -1
        for (s in stops) {
            val i = pending.indexOf(s)
            if (i >= 0 && (hit < 0 || i < hit)) hit = i
        }
        if (hit >= 0) {
            val out = pending.substring(0, hit)
            pending.setLength(0)
            stopped = true
            return out
        }

        val keep = longestSuffixThatStartsAStop()
        val emitLen = pending.length - keep
        val out = pending.substring(0, emitLen)
        pending.delete(0, emitLen)
        return out
    }

    /** Releases held-back text at the end of the stream. */
    fun flush(): String {
        val out = pending.toString()
        pending.setLength(0)
        return out
    }

    private fun longestSuffixThatStartsAStop(): Int {
        var best = 0
        for (s in stops) {
            val max = minOf(s.length - 1, pending.length)
            for (len in max downTo 1) {
                if (len <= best) break
                if (pending.endsWith(s.substring(0, len))) {
                    best = len
                    break
                }
            }
        }
        return best
    }
}

/**
 * Applies [stop] sequences to a provider stream. When a stop sequence is hit, the upstream
 * is cancelled (providers abort native generation on cancellation) and a final
 * [GenerationEvent.Done] with finish reason "stop" is emitted.
 */
fun Flow<GenerationEvent>.applyStops(stop: List<String>): Flow<GenerationEvent> {
    if (stop.none { it.isNotEmpty() }) return this
    val upstream = this
    return flow {
        val filter = StopSequenceFilter(stop)
        var completionTokens = 0
        try {
            upstream.collect { event ->
                when (event) {
                    is GenerationEvent.Text -> {
                        completionTokens++
                        val out = filter.push(event.text)
                        if (out.isNotEmpty()) emit(GenerationEvent.Text(out))
                        if (filter.stopped) throw StopReached()
                    }
                    is GenerationEvent.ToolCalls -> emit(event)
                    is GenerationEvent.Done -> {
                        val rest = filter.flush()
                        if (rest.isNotEmpty()) emit(GenerationEvent.Text(rest))
                        emit(event)
                    }
                }
            }
        } catch (e: StopReached) {
            // Upstream was cancelled before it could report prompt tokens: 0 = unknown,
            // the HTTP layer falls back to an estimate.
            emit(
                GenerationEvent.Done(
                    finishReason = FinishReason.STOP,
                    promptTokens = 0,
                    completionTokens = completionTokens
                )
            )
        }
    }
}

private class StopReached : RuntimeException() {
    override fun fillInStackTrace(): Throwable = this
}

/** Collects a whole generation into a [GenerationResult] (non-streaming responses). */
suspend fun Flow<GenerationEvent>.collectResult(): GenerationResult {
    val text = StringBuilder()
    val calls = mutableListOf<ToolCallData>()
    var done: GenerationEvent.Done? = null
    collect { event ->
        when (event) {
            is GenerationEvent.Text -> text.append(event.text)
            is GenerationEvent.ToolCalls -> calls.addAll(event.calls)
            is GenerationEvent.Done -> done = event
        }
    }
    val d = done
    val finish = when {
        calls.isNotEmpty() -> FinishReason.TOOL_CALLS
        d != null -> d.finishReason
        else -> FinishReason.STOP
    }
    return GenerationResult(
        text = text.toString(),
        toolCalls = calls,
        finishReason = finish,
        promptTokens = d?.promptTokens ?: 0,
        completionTokens = d?.completionTokens ?: estimateTokens(text.toString()),
        cachedPromptTokens = d?.cachedPromptTokens ?: 0
    )
}
