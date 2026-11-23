package com.github.ljarka.picfort.core.network

/*
 * Setup mistakes and their fixes (T-086), in plain words, for the setup page and Settings. Each one
 * says what is wrong and what to do; INSTALL.md's steps are named where the fix is on the server.
 */

/** Picfort's default port (`docker/example.env`), Immich's default port. */
private const val PICFORT_PORT = 5000
private const val IMMICH_PORT = 2283

/** What waitress sends as `Server` (picfort-server `serve(..., ident="picfort")`). */
private const val PICFORT_SERVER_HEADER = "picfort"

/** macOS AirPlay Receiver answers on port 5000 with this `Server` header. */
private fun isAirPlay(serverHeader: String?) = serverHeader?.startsWith("AirTunes", ignoreCase = true) == true

private fun airPlayMessage(port: Int) = "Port $port on this Mac is AirPlay Receiver, not Picfort. " +
    "Set PICFORT_PORT=5050 in the server's .env, start it again, and use :5050 here."

fun picfortUnreachableMessage(host: String, port: Int): String = "Can't reach the Picfort server at $host:$port. " +
    "Check that the phone is on the home network, the port ($PICFORT_PORT by default), " +
    "and that the server is running (docker compose ps)."

/** `/status` answered, but not as a Picfort server: says what probably answered instead. */
fun notPicfortMessage(host: String, port: Int, code: Int, serverHeader: String?, body: String?): String = when {
    isAirPlay(serverHeader) -> airPlayMessage(port)

    port == IMMICH_PORT || body?.contains("immich", ignoreCase = true) == true ->
        "That's Immich's address. The Picfort server has its own port: $PICFORT_PORT by default."

    else ->
        "Something answers at $host:$port, but it isn't a Picfort server (HTTP $code). " +
            "Check the port: $PICFORT_PORT by default."
}

/**
 * The Picfort server's own view of Immich (`/status` "immich"): null when it works or the server is
 * older and doesn't say.
 */
fun picfortImmichProblem(immich: String?): String? = when (immich) {
    "unreachable" ->
        "Connected, but the server can't reach Immich. Check IMMICH_URL in the server's .env: " +
            "with Docker, localhost is the container itself (INSTALL.md, step 3)."

    "not_immich" ->
        "Connected, but IMMICH_URL in the server's .env doesn't point to Immich. " +
            "Check its address and port ($IMMICH_PORT by default)."

    "key_rejected" ->
        "Connected, but Immich rejects the server's API key. Create a new one " +
            "(INSTALL.md, step 2) and put it in IMMICH_API_KEY."

    else -> null
}

fun immichUnreachableMessage(baseUrl: String): String = "Can't connect to $baseUrl. " +
    "Check that the phone is on the home network and the address and port ($IMMICH_PORT by default)."

/** The Immich address answered, but not as Immich: says what probably answered instead. */
fun notImmichMessage(baseUrl: String, code: Int, serverHeader: String?, port: Int): String = when {
    serverHeader.equals(PICFORT_SERVER_HEADER, ignoreCase = true) ->
        "That's the Picfort server's address. Immich has its own port: $IMMICH_PORT by default."

    isAirPlay(serverHeader) ->
        "Port $port on this Mac is AirPlay Receiver, not Immich. " +
            "Immich's port is $IMMICH_PORT by default."

    else -> "No Immich server found at $baseUrl (HTTP $code). Check the port: Immich uses $IMMICH_PORT by default."
}
