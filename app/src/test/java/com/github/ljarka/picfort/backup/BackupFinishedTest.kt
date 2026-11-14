package com.github.ljarka.picfort.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** T-006: what a "Back up now" run leaves in the notifications when it ends. */
class BackupFinishedTest {
    private val done = BackupOutcome.Done(
        uploaded = 3,
        alreadyOnServer = 0,
        rejected = 0,
        waitingForHome = 0,
        viaHomeNetwork = false,
    )

    @Test
    fun `a finished run says so`() {
        assertEquals("Backup complete", backupFinishedTitle(done, retrying = false))
    }

    @Test
    fun `a failed run says it didn't finish`() {
        assertEquals("Backup didn't finish", backupFinishedTitle(BackupOutcome.NoMediaAccess, retrying = false))
        assertEquals("Backup didn't finish", backupFinishedTitle(null, retrying = false)) // e.g. HTTP 403
    }

    @Test
    fun `nothing while it will retry or when the quota has its own notification`() {
        assertNull(backupFinishedTitle(null, retrying = true))
        assertNull(backupFinishedTitle(BackupOutcome.QuotaExceeded(uploaded = 2), retrying = false))
    }
}
