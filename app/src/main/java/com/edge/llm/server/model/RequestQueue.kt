package com.edge.llm.server.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * Eccezione generata quando la coda di inferenza raggiunge la capacità massima consentita.
 * Mappa direttamente a HTTP 429 Too Many Requests (rate_limit_exceeded).
 */
class QueueFullException(
    message: String = "Server is busy processing other requests. Queue capacity reached. Please retry later."
) : Exception(message)

/**
 * Eccezione generata quando una richiesta attende in coda oltre il tempo limite massimo.
 * Mappa a HTTP 429 Too Many Requests con Retry-After.
 */
class QueueTimeoutException(
    message: String = "Request timed out waiting in inference queue."
) : Exception(message)

/**
 * RequestQueue: Gestore della concorrenza single-worker serializzato per l'inferenza LLM su dispositivo.
 * 
 * Regole operative (Fable 5 Sessione S2 & ADR 13):
 * - Esattamente 1 inferenza alla volta (esclusione mutua tramite workerMutex).
 * - Coda di attesa FIFO con capienza massima [maxQueueDepth] (default: 4 slot).
 * - Se la coda è satura, le nuove richieste vengono rifiutate immediatamente con [QueueFullException].
 * - Timeout massimo di attesa in coda [queueTimeoutMs] (default: 120_000 ms).
 * - Le richieste in streaming trattengono il lock del worker fino al completamento o cancellazione del flusso.
 */
class RequestQueue(
    val maxQueueDepth: Int = 4,
    val queueTimeoutMs: Long = 120_000L
) {
    private val workerMutex = Mutex()
    private val _queuedRequests = AtomicInteger(0)
    private val _activeRequests = AtomicInteger(0)

    /** Numero di richieste attualmente in attesa nella coda FIFO */
    val queuedRequestsCount: Int get() = _queuedRequests.get()

    /** Numero di richieste attualmente in esecuzione (0 oppure 1) */
    val activeRequestsCount: Int get() = _activeRequests.get()

    /** Indica se il worker è occupato in inferenza o ci sono richieste in coda */
    val isBusy: Boolean get() = activeRequestsCount > 0 || queuedRequestsCount > 0

    /**
     * Esegue una computazione non-streaming in modo strettamente serializzato.
     * 
     * @throws QueueFullException se ci sono già [maxQueueDepth] richieste in attesa.
     * @throws QueueTimeoutException se l'attesa per acquisire il worker supera [queueTimeoutMs].
     */
    suspend fun <T> execute(block: suspend () -> T): T {
        // 1. Riserva lo slot in coda (fallimento immediato se satura)
        while (true) {
            val current = _queuedRequests.get()
            if (current >= maxQueueDepth) {
                throw QueueFullException(
                    "Server is busy. Queue capacity ($maxQueueDepth) reached. Please retry later."
                )
            }
            if (_queuedRequests.compareAndSet(current, current + 1)) {
                break
            }
        }

        var acquired = false
        try {
            // 2. Attende il turno FIFO con timeout
            val acquiredInTime = withTimeoutOrNull(queueTimeoutMs) {
                workerMutex.lock()
                true
            } ?: false

            if (!acquiredInTime) {
                throw QueueTimeoutException(
                    "Request timed out after ${queueTimeoutMs}ms waiting in inference queue."
                )
            }
            acquired = true
            _queuedRequests.decrementAndGet()
            _activeRequests.incrementAndGet()

            // 3. Esegue l'inferenza protetta dal lock
            return block()
        } finally {
            if (acquired) {
                _activeRequests.decrementAndGet()
                workerMutex.unlock()
            } else {
                _queuedRequests.decrementAndGet()
            }
        }
    }

    /**
     * Esegue una computazione in streaming in modo serializzato, garantendo che il lock del worker
     * venga acquisito prima di invocare [consumer] e rilasciato solo quando il flusso
     * termina o viene interrotto/cancellato.
     * 
     * @throws QueueFullException se la coda è satura prima dell'acquisizione.
     * @throws QueueTimeoutException se il timeout di accodamento scade.
     */
    suspend fun executeStream(
        flowProvider: suspend () -> Flow<String>,
        consumer: suspend (Flow<String>) -> Unit
    ) {
        // 1. Riserva lo slot in coda
        while (true) {
            val current = _queuedRequests.get()
            if (current >= maxQueueDepth) {
                throw QueueFullException(
                    "Server is busy. Queue capacity ($maxQueueDepth) reached. Please retry later."
                )
            }
            if (_queuedRequests.compareAndSet(current, current + 1)) {
                break
            }
        }

        var acquired = false
        try {
            // 2. Attesa acquisizione worker
            val acquiredInTime = withTimeoutOrNull(queueTimeoutMs) {
                workerMutex.lock()
                true
            } ?: false

            if (!acquiredInTime) {
                throw QueueTimeoutException(
                    "Request timed out after ${queueTimeoutMs}ms waiting in inference queue."
                )
            }
            acquired = true
            _queuedRequests.decrementAndGet()
            _activeRequests.incrementAndGet()

            // 3. Esecuzione stream
            val flow = flowProvider()
            consumer(flow)
        } finally {
            if (acquired) {
                _activeRequests.decrementAndGet()
                workerMutex.unlock()
            } else {
                _queuedRequests.decrementAndGet()
            }
        }
    }
}
