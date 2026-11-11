package com.github.ljarka.picfort.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE = "com.github.ljarka.immich.android2"

/**
 * Frame times while flinging through the timeline. Needs a logged-in app with photos
 * (server and/or phone); it does not log in by itself.
 *
 * Run: ./gradlew :benchmark:connectedBenchmarkAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class TimelineScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun scrollTimeline() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Max)),
        compilationMode = CompilationMode.Partial(),
        // No startupMode: COLD would kill the app after setupBlock, before the scroll.
        iterations = 8,
        setupBlock = {
            // Fresh process each time: thumbnails come from Coil's disk cache, not memory.
            killProcess()
            startActivityAndWait()
            // The app opens on the Family Wall; the timeline is the Photos tab.
            val photosTab = device.wait(Until.findObject(By.text("Photos")), 15_000)
            checkNotNull(photosTab) { "Bottom bar not shown: is the app logged in?" }.click()
            check(device.wait(Until.hasObject(By.text("Timeline")), 15_000) && device.wait(Until.hasObject(By.scrollable(true)), 15_000)) {
                "Timeline not shown after opening the Photos tab"
            }
        },
    ) {
        // The grid is recreated while months load, so look it up before every fling.
        fun fling(direction: Direction) {
            val grid = device.wait(Until.findObject(By.scrollable(true)), 5_000) ?: return
            // The grid reaches under the status bar; keep gestures off the top edge so
            // flinging up doesn't pull down the notification shade.
            val margin = device.displayWidth / 5
            grid.setGestureMargins(margin, device.displayHeight / 4, margin, device.displayHeight / 8)
            try {
                grid.fling(direction)
            } catch (e: StaleObjectException) {
                return
            }
            device.waitForIdle()
        }
        repeat(4) { fling(Direction.DOWN) }
        repeat(2) { fling(Direction.UP) }
    }
}
