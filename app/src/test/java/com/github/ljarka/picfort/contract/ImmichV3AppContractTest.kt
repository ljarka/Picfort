package com.github.ljarka.picfort.contract

import com.github.ljarka.picfort.backup.BulkUploadCheckResponse
import com.github.ljarka.picfort.backup.ServerVersion
import com.github.ljarka.picfort.backup.UploadResponse
import com.github.ljarka.picfort.ui.login.LoginResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Login and backup responses of Immich 3.3, read with the app's classes (see ImmichV3ContractTest). */
class ImmichV3AppContractTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun read(name: String): String =
        checkNotNull(javaClass.getResource("/immich-v3/$name")) { "missing fixture $name" }.readText()

    @Test
    fun `login, version, backup`() {
        assertEquals("token", json.decodeFromString<LoginResponse>(read("login.json")).accessToken)
        assertEquals(3, json.decodeFromString<ServerVersion>(read("version.json")).major)
        assertEquals(
            "accept",
            json.decodeFromString<BulkUploadCheckResponse>(read("bulk_check.json")).results.single().action,
        )
        assertEquals("created", json.decodeFromString<UploadResponse>(read("upload.json")).status)
    }
}
