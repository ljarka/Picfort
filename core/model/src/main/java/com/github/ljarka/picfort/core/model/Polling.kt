package com.github.ljarka.picfort.core.model

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Runs [block] forever with a pause between runs.
 *
 * With [includeRunTimeInInterval] set to false (default), each run is followed by [intervalMillis].
 * With it set to true, [intervalMillis] is the full start-to-start period, bounded by [minDelayMillis].
 */
suspend fun pollEvery(
    intervalMillis: Long,
    minDelayMillis: Long = intervalMillis,
    includeRunTimeInInterval: Boolean = false,
    block: suspend () -> Unit,
) {
    require(intervalMillis > 0) { "intervalMillis must be > 0" }
    require(minDelayMillis > 0) { "minDelayMillis must be > 0" }
    while (currentCoroutineContext().isActive) {
        val startedAtNanos = System.nanoTime()
        block()
        val elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L
        delay(
            nextPollDelayMillis(
                intervalMillis = intervalMillis,
                elapsedMillis = elapsedMillis,
                minDelayMillis = minDelayMillis,
                includeRunTimeInInterval = includeRunTimeInInterval,
            )
        )
    }
}

internal fun nextPollDelayMillis(
    intervalMillis: Long,
    elapsedMillis: Long,
    minDelayMillis: Long,
    includeRunTimeInInterval: Boolean,
): Long = if (includeRunTimeInInterval) {
    (intervalMillis - elapsedMillis).coerceAtLeast(minDelayMillis)
} else {
    intervalMillis
}
