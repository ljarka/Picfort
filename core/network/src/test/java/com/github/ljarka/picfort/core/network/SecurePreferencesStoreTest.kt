package com.github.ljarka.picfort.core.network

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SecurePreferencesStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeCipher(private var generation: Int = 1) : SecureCipher {
        val resetCount = AtomicInteger(0)

        override fun encrypt(plainText: String): String = "v$generation:$plainText"

        override fun decrypt(value: String): String {
            val prefix = "v$generation:"
            if (!value.startsWith(prefix)) {
                throw IllegalStateException("Keystore key invalidated for payload: $value")
            }
            return value.removePrefix(prefix)
        }

        override fun resetKey() {
            generation++
            resetCount.incrementAndGet()
        }
    }

    @Test
    fun recoversFromCorruptOrUndecryptablePreferences() = runBlocking {
        val file = File(tempFolder.newFolder(), "secure_test.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
            // Seed DataStore with ciphertext from a lost Keystore key ("v1:...").
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("access_token")] = "v1:secret-token"
            }

            val recovered = AtomicInteger(0)
            // Current cipher is on generation 2 -> decrypting "v1:..." fails like a lost Keystore key.
            val cipher = FakeCipher(generation = 2)
            val store = SecurePreferencesStore(
                dataStore = dataStore,
                appScope = scope,
                cipher = cipher,
                ioDispatcher = Dispatchers.IO,
                onRecovery = { recovered.incrementAndGet() },
                onWriteError = {},
            )

            // Does not crash; starts clean so the user lands on login.
            assertEquals("", store.getString("access_token"))
            assertEquals(1, recovered.get())
            assertEquals(1, cipher.resetCount.get())
            assertTrue(dataStore.data.first().asMap().isEmpty())

            // Writing and reading after recovery works with the recreated key.
            store.putString("access_token", "fresh-token")
            assertEquals("fresh-token", store.getString("access_token"))

            // Wait briefly for async persistence to land in DataStore.
            var persisted: String? = null
            repeat(50) {
                persisted = dataStore.data.first()[stringPreferencesKey("access_token")]
                if (persisted != null) return@repeat
                Thread.sleep(20)
            }
            assertEquals("v3:fresh-token", persisted)
        } finally {
            scope.cancel()
        }
    }
}
