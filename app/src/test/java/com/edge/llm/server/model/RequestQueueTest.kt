package com.edge.llm.server.model

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Collections

class RequestQueueTest {

    @Test
    fun testSingleExecutionReturnsResultAndResetsCounters() = runBlocking {
        val queue = RequestQueue(maxQueueDepth = 4, queueTimeoutMs = 5000L)
        
        val result = queue.execute {
            assertEquals(1, queue.activeRequestsCount)
            assertEquals(0, queue.queuedRequestsCount)
            "inference_output"
        }

        assertEquals("inference_output", result)
        assertEquals(0, queue.activeRequestsCount)
        assertEquals(0, queue.queuedRequestsCount)
        assertFalse(queue.isBusy)
    }

    @Test
    fun testFifoOrderExecution() = runBlocking {
        val queue = RequestQueue(maxQueueDepth = 4, queueTimeoutMs = 5000L)
        val executionOrder = Collections.synchronizedList(mutableListOf<Int>())

        // Primo task blocca il worker per un breve tempo
        val job1 = launch {
            queue.execute {
                delay(100)
                executionOrder.add(1)
            }
        }

        // Dare tempo a job1 di acquisire il lock
        delay(20)

        // I task successivi devono accodarsi ed eseguire nell'ordine di sottomissione (FIFO)
        val job2 = launch {
            queue.execute {
                delay(30)
                executionOrder.add(2)
            }
        }

        val job3 = launch {
            queue.execute {
                delay(10)
                executionOrder.add(3)
            }
        }

        job1.join()
        job2.join()
        job3.join()

        assertEquals(listOf(1, 2, 3), executionOrder)
        assertEquals(0, queue.activeRequestsCount)
        assertEquals(0, queue.queuedRequestsCount)
    }

    @Test
    fun testQueueOverflowThrowsQueueFullExceptionImmediately() = runBlocking {
        val queue = RequestQueue(maxQueueDepth = 4, queueTimeoutMs = 5000L)

        // 1 request attiva
        val blockerJob = launch {
            queue.execute {
                delay(1000)
            }
        }
        delay(30) // assicura acquisizione

        // 4 richieste in coda
        val queuedJobs = (1..4).map { id ->
            launch {
                queue.execute {
                    delay(50)
                }
            }
        }
        delay(50) // assicura accodamento di tutti e 4

        assertEquals(1, queue.activeRequestsCount)
        assertEquals(4, queue.queuedRequestsCount)

        // La 5a richiesta in attesa deve eccedere la capacità 4 e lanciare QueueFullException
        var exceptionThrown = false
        try {
            queue.execute {
                "overflow"
            }
            fail("Dovrebbe lanciare QueueFullException")
        } catch (e: QueueFullException) {
            exceptionThrown = true
            assertTrue(e.message?.contains("Queue capacity") == true)
        }

        assertTrue("QueueFullException deve essere lanciata", exceptionThrown)

        // Cleanup
        blockerJob.cancel()
        queuedJobs.forEach { it.cancel() }
    }

    @Test
    fun testQueueTimeoutThrowsQueueTimeoutException() = runBlocking {
        // Coda con timeout brevissimo (100ms)
        val queue = RequestQueue(maxQueueDepth = 4, queueTimeoutMs = 100L)

        val blockerJob = launch {
            queue.execute {
                delay(500)
            }
        }
        delay(20)

        var timeoutThrown = false
        try {
            queue.execute {
                "should_timeout"
            }
            fail("Dovrebbe lanciare QueueTimeoutException")
        } catch (e: QueueTimeoutException) {
            timeoutThrown = true
            assertTrue(e.message?.contains("timed out") == true)
        }

        assertTrue("QueueTimeoutException deve essere lanciata", timeoutThrown)
        blockerJob.cancel()
    }

    @Test
    fun testStreamExecutionHoldsWorkerLockUntilConsumerFinishes() = runBlocking {
        val queue = RequestQueue(maxQueueDepth = 4, queueTimeoutMs = 5000L)
        val tokensEmitted = mutableListOf<String>()
        var secondRequestFinished = false

        // Stream flow
        val streamJob = launch {
            queue.executeStream(
                flowProvider = {
                    flow {
                        emit("token1")
                        delay(60)
                        emit("token2")
                        delay(60)
                        emit("token3")
                    }
                },
                consumer = { flow ->
                    flow.collect { token ->
                        tokensEmitted.add(token)
                    }
                }
            )
        }

        delay(30) // Stream avviato
        assertEquals(1, queue.activeRequestsCount)

        // Seconda richiesta attende che lo stream sia completato
        val secondJob = launch {
            queue.execute {
                secondRequestFinished = true
            }
        }

        delay(20)
        assertFalse("La seconda richiesta non può finire mentre lo stream è aperto", secondRequestFinished)

        streamJob.join()
        secondJob.join()

        assertEquals(listOf("token1", "token2", "token3"), tokensEmitted)
        assertTrue(secondRequestFinished)
        assertEquals(0, queue.activeRequestsCount)
    }
}
