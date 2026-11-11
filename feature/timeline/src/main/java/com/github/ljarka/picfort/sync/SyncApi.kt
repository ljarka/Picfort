package com.github.ljarka.picfort.sync

import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming

/** `reset` is left out of the JSON when false: the server's schema rejects `null`. */
@Serializable
data class SyncStreamRequest(val types: List<String>, val reset: Boolean = false)

@Serializable
data class SyncAckRequest(val acks: List<String>)

/**
 * Immich's sync protocol (stable since v2). Needs a session token: every sync endpoint
 * answers 403 to API keys. See ADR 0005.
 */
interface SyncApi {

    /** JSON lines; read it line by line, a first sync can be tens of MB. */
    @Streaming
    @POST("/api/sync/stream")
    suspend fun stream(@Body body: SyncStreamRequest): ResponseBody

    /** Stores the checkpoint for this login session; ≤ 1000 acks per call. */
    @POST("/api/sync/ack")
    suspend fun ack(@Body body: SyncAckRequest): Response<Unit>
}
