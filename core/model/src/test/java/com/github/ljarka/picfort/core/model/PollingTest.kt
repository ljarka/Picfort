package com.github.ljarka.picfort.core.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class PollingTest {

    @Test
    fun nextDelayUsesIntervalWhenRuntimeIsIgnored() {
        assertEquals(
            60L,
            nextPollDelayMillis(
                intervalMillis = 60L,
                elapsedMillis = 120L,
                minDelayMillis = 5L,
                includeRunTimeInInterval = false,
            )
        )
    }

    @Test
    fun nextDelaySubtractsRuntimeAndRespectsMinimum() {
        assertEquals(
            45L,
            nextPollDelayMillis(
                intervalMillis = 60L,
                elapsedMillis = 15L,
                minDelayMillis = 5L,
                includeRunTimeInInterval = true,
            )
        )
        assertEquals(
            5L,
            nextPollDelayMillis(
                intervalMillis = 60L,
                elapsedMillis = 80L,
                minDelayMillis = 5L,
                includeRunTimeInInterval = true,
            )
        )
    }

    @Test
    fun pollEveryRunsUntilCancelledByCaller() {
        var runs = 0
        try {
            runBlocking {
                pollEvery(intervalMillis = 1L) {
                    runs += 1
                    if (runs == 3) throw CancellationException("done")
                }
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            // expected
        }
        assertEquals(3, runs)
    }

    @Test
    fun pollEveryRequiresPositiveIntervals() {
        assertIllegalArgument {
            runBlocking { pollEvery(intervalMillis = 0L) {} }
        }
        assertIllegalArgument {
            runBlocking { pollEvery(intervalMillis = 10L, minDelayMillis = 0L) {} }
        }
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
