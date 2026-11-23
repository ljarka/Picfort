package com.github.ljarka.picfort.core.network

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T-086: each common setup mistake gets a message that says what to do. */
class SetupHintsTest {
    private fun assertMentions(message: String?, vararg parts: String) {
        parts.forEach { assertTrue("\"$it\" not in: $message", message?.contains(it) == true) }
    }

    @Test
    fun `Picfort address answered by something else`() {
        assertMentions(
            notPicfortMessage("192.168.1.20", 5000, 403, "AirTunes/860.7.1", null),
            "AirPlay Receiver",
            "PICFORT_PORT=5050",
        )
        assertMentions(notPicfortMessage("192.168.1.20", 2283, 404, null, null), "Immich's address", "5000")
        assertMentions(
            notPicfortMessage("nas", 8080, 200, "nginx", "<title>Immich</title>"),
            "Immich's address",
        )
        assertMentions(
            notPicfortMessage("192.168.1.20", 8080, 404, "nginx", "Not Found"),
            "isn't a Picfort server (HTTP 404)",
            "5000",
        )
    }

    @Test
    fun `Picfort server not reachable`() {
        assertMentions(
            picfortUnreachableMessage("192.168.1.20", 5000),
            "192.168.1.20:5000",
            "home network",
            "docker compose ps",
        )
    }

    @Test
    fun `the server's view of Immich`() {
        assertNull(picfortImmichProblem("ok"))
        assertNull(picfortImmichProblem(null))
        assertNull(picfortImmichProblem("something new"))
        assertMentions(picfortImmichProblem("unreachable"), "IMMICH_URL", "localhost", "INSTALL.md, step 3")
        assertMentions(picfortImmichProblem("not_immich"), "IMMICH_URL", "2283")
        assertMentions(picfortImmichProblem("key_rejected"), "API key", "IMMICH_API_KEY", "INSTALL.md, step 2")
    }

    @Test
    fun `Immich address answered by something else`() {
        val base = "http://192.168.1.20:5000"
        assertMentions(notImmichMessage(base, 404, "picfort", 5000), "Picfort server's address", "2283")
        assertMentions(notImmichMessage(base, 403, "AirTunes/860.7.1", 5000), "AirPlay Receiver", "2283")
        assertMentions(notImmichMessage("http://nas:8080", 404, "nginx", 8080), "No Immich server", "HTTP 404", "2283")
        assertMentions(immichUnreachableMessage("http://nas:2283"), "http://nas:2283", "home network")
    }
}
