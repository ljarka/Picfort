package com.github.ljarka.picfort.ui.frame

import com.github.ljarka.picfort.core.network.PicfortServerException
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameSettingsDialogTest {

    @Test
    fun `connection test messages`() {
        assertTrue(wallTestResult(null) is ConnectionTest.Ok)
        listOf(
            PicfortServerException.NotAccepted() to "didn't accept your Immich login",
            PicfortServerException.NoWall() to "no Family Wall yet",
            PicfortServerException.ImmichUnreachable() to "can't reach Immich",
            PicfortServerException.Unreachable("x") to "didn't answer",
        ).forEach { (error, expected) ->
            val result = wallTestResult(error)
            assertTrue("$error", result is ConnectionTest.Partial && expected in result.message)
        }
    }
}
