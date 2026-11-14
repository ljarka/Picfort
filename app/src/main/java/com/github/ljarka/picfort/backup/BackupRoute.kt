package com.github.ljarka.picfort.backup

import com.github.ljarka.picfort.network.normalizeServerUrl
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit

/**
 * Largest upload through the public address. Cloudflare's proxy refuses bodies over 100 MB with
 * HTTP 413 (checked on the test server, T-064); a little is left for the multipart headers.
 */
const val PUBLIC_UPLOAD_LIMIT = 99_000_000L

/** `local_backup.rejected` for a file the public address refused as too large: waits for home. */
const val NEEDS_HOME_NETWORK = "HTTP 413"

/** A 413 through the home address: the server itself refuses the size. Not retried. */
const val TOO_LARGE_FOR_SERVER = "HTTP 413 (home network)"

/**
 * Whether to leave a file for a run through the home address: one the public address already
 * refused as too large, or, when a home address is set, anything over [PUBLIC_UPLOAD_LIMIT].
 */
fun holdForHomeNetwork(size: Long, rejected: String?, onHomeNetwork: Boolean, homeAddressSet: Boolean): Boolean =
    !onHomeNetwork && (rejected == NEEDS_HOME_NETWORK || (homeAddressSet && size > PUBLIC_UPLOAD_LIMIT))

/**
 * Backup's way to Immich (T-064). At home the server's local address (e.g. http://192.168.0.10:2283)
 * skips the public proxy and its size limit; elsewhere uploads go through the usual address.
 */
class BackupRoute @Inject constructor(
    private val client: OkHttpClient,
    private val retrofit: Retrofit,
    private val settings: BackupSettingsStore,
) {
    /** The home address, if one is set and Immich answers there now. */
    suspend fun reachableHome(): HttpUrl? {
        val home = normalizeServerUrl(settings.homeUrl) ?: return null
        return home.takeIf { ping(it) }
    }

    fun service(base: HttpUrl): BackupService =
        retrofit.newBuilder().baseUrl(base).build().create(BackupService::class.java)

    private suspend fun ping(base: HttpUrl): Boolean = withContext(Dispatchers.IO) {
        val quick = client.newBuilder().callTimeout(3, TimeUnit.SECONDS).build()
        val url = base.newBuilder().encodedPath("/api/server/ping").build()
        try {
            quick.newCall(Request.Builder().url(url).build()).execute().use { it.isSuccessful }
        } catch (e: IOException) {
            false // not at home, or the address is wrong
        }
    }
}
