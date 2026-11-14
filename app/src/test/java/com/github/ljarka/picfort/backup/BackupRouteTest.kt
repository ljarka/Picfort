package com.github.ljarka.picfort.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRouteTest {
    private val big = 213_000_000L
    private val small = 5_000_000L

    @Test
    fun `at home everything goes`() {
        assertFalse(holdForHomeNetwork(big, NEEDS_HOME_NETWORK, onHomeNetwork = true, homeAddressSet = true))
    }

    @Test
    fun `away, files the public address refused wait, with or without a home address`() {
        assertTrue(holdForHomeNetwork(big, NEEDS_HOME_NETWORK, onHomeNetwork = false, homeAddressSet = true))
        assertTrue(holdForHomeNetwork(big, NEEDS_HOME_NETWORK, onHomeNetwork = false, homeAddressSet = false))
    }

    @Test
    fun `away with a home address, big files wait without a try, small ones go`() {
        assertTrue(holdForHomeNetwork(big, null, onHomeNetwork = false, homeAddressSet = true))
        assertFalse(holdForHomeNetwork(small, null, onHomeNetwork = false, homeAddressSet = true))
    }

    @Test
    fun `without a home address a big file is tried once (the server may take it)`() {
        assertFalse(holdForHomeNetwork(big, null, onHomeNetwork = false, homeAddressSet = false))
        assertFalse(holdForHomeNetwork(big, TOO_LARGE_FOR_SERVER, onHomeNetwork = false, homeAddressSet = false))
    }

    @Test
    fun summary() {
        assertEquals(
            "Home network · Uploaded 3 · 2 were already in Immich",
            backupSummary(BackupOutcome.Done(3, 2, 0, viaHomeNetwork = true), homeAddressSet = true),
        )
        assertEquals(
            "Uploaded 0 · 1 over 100 MB wait for the home network",
            backupSummary(BackupOutcome.Done(0, 0, 0, waitingForHome = 1), homeAddressSet = true),
        )
        assertEquals(
            "Uploaded 0 · 1 too large for the server's public address: set the home network address",
            backupSummary(BackupOutcome.Done(0, 0, 0, waitingForHome = 1), homeAddressSet = false),
        )
        assertEquals("Up to date", backupSummary(BackupOutcome.Done(0, 0, 0), homeAddressSet = false))
    }
}
